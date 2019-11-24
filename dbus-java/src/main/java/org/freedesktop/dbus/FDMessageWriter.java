package org.freedesktop.dbus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;
import jnr.posix.CmsgHdr;
import jnr.posix.MsgHdr;
import jnr.posix.POSIXFactory;
import org.freedesktop.Hexdump;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A MessageWriter that handles writing out messages directly to a FileDescriptor,
 * not to an OutputStream.
 */
public class FDMessageWriter implements MessageWriter {
    
    private static jnr.posix.POSIX POSIX = POSIXFactory.getPOSIX();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    // Constants that are macros in C - these may change between systems
    private static final int SOL_SOCKET = 1;
    private static final int SCM_RIGHTS = 1;
    private static final int MSG_NOSIGNAL = 16384;
    
    private int m_fd;
    private boolean m_isClosed;
    
    public FDMessageWriter( int fd ){
        m_fd = fd;
        m_isClosed = false;
    }

    @Override
    public void close() throws IOException {
        m_isClosed = true;
        POSIX.close( m_fd );
    }

    @Override
    public void writeMessage(Message m) throws IOException {
        logger.debug("<= {}", m);
        if (null == m) {
            return;
        }
        if (null == m.getWireData()) {
            logger.warn("Message {} wire-data was null!", m);
            return;
        }
        
        MsgHdr msghdr = POSIX.allocateMsgHdr();
        List<FileDescriptor> fds = m.getFiledescriptors();
        
        // Set the file descriptors that we need to send
        if( fds.size() > 0 ){
            CmsgHdr cmsghdr = msghdr.allocateControl(fds.size());
            cmsghdr.setType(SOL_SOCKET);
            cmsghdr.setLevel(SCM_RIGHTS);
            ByteBuffer bb = ByteBuffer.allocate(fds.size() * 4);
            IntBuffer ibuf = bb.asIntBuffer();
            for( FileDescriptor fd : fds ){
                ibuf.put( fd.getIntFileDescriptor() );
            }
            cmsghdr.setData(bb);
        }
        
        int data_len = 0;

        // First figure out how big our data needs to be...
        for (byte[] buf : m.getWireData()) {
            if(logger.isTraceEnabled()) {
                logger.trace("{}", null == buf ? "" : Hexdump.format(buf));
            }
            if (null == buf) {
                break;
            }
            
            data_len += buf.length;
        }
        
        // ... now allocate enough data and loop again to set it all.
        ByteBuffer dataSend = ByteBuffer.allocate(data_len);
        for (byte[] buf : m.getWireData()) {
            if (null == buf) {
                break;
            }
            
            dataSend.put(buf);
        }
        
        dataSend.flip();
        logger.debug( "pointer is {}", 
                Pointer.wrap( jnr.ffi.Runtime.getSystemRuntime(), dataSend ) );
        msghdr.setIov(new ByteBuffer[]{dataSend});
        
        logger.trace( "msghdr to send: {}", msghdr );
        
        int bytesWrote = POSIX.sendmsg(m_fd, msghdr, MSG_NOSIGNAL);
        if( bytesWrote < 0 ){
            int errno = POSIX.errno();
            throw new IOException( "Unable to send data: " + POSIX.strerror(errno) );
        }
    }

    @Override
    public boolean isClosed() {
        return m_isClosed;
    }
    
}
