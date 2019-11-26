package org.freedesktop.dbus;

import java.io.IOException;
import java.nio.ByteBuffer;
import jnr.posix.MsgHdr;
import jnr.posix.POSIXFactory;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read messages from the bus using a file descriptor.
 */
public class FDMessageReader implements MessageReader {
    
    private static jnr.posix.POSIX POSIX = POSIXFactory.getPOSIX();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    // Constants that are macros in C - these may change between systems
    private static final int MSG_PEEK = 2;
    
    private int m_fd;
    private boolean m_isClosed;
    
    public FDMessageReader(int fd){
        m_fd = fd;
        m_isClosed = false;
    }

    @Override
    public boolean isClosed() {
        return m_isClosed;
    }

    @Override
    public Message readMessage() throws IOException, DBusException {
        MsgHdr inMessage = POSIX.allocateMsgHdr();
        ByteBuffer[] inData = new ByteBuffer[2];
        int bytesRead = 0;
        
        // Note: Since we need to read the entire message at once, peek
        // at the header data to see how much we need.
        inData[0] = ByteBuffer.allocateDirect(12);
        
        while( bytesRead != 12 ){
            bytesRead = POSIX.recvmsg(m_fd, inMessage, MSG_PEEK);
            if( bytesRead < 0 ){
                int errno = POSIX.errno();
                throw new IOException( "Unable to receive data: " + POSIX.strerror(errno) );
            }
        }
        
        inData[0].flip();
        
        /* Parse the details from the header */
        byte endian = inData[0].get(0);
        byte type = inData[0].get(1);
        byte protover = inData[0].get(3);
        if (protover > Message.PROTOCOL) {
            //buf = null;
            throw new MessageProtocolVersionException(String.format("Protocol version %s is unsupported", protover));
        }
        
        return null;
    }

    @Override
    public void close() throws IOException {
        if( m_isClosed ) return;
        m_isClosed = true;
        POSIX.close( m_fd );
    }
    
}
