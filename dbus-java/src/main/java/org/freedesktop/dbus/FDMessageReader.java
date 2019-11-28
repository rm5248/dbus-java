package org.freedesktop.dbus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import jnr.posix.MsgHdr;
import jnr.posix.POSIXFactory;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.messages.MessageFactory;
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
        ByteBuffer[] inData = new ByteBuffer[1];
        int bytesRead = 0;
        int totalMessageLen;
        int headerArrayLen;
        int messageBodyLen;
        Message m;
        
        // Note: Since we need to read the entire message at once, peek
        // at the header data to see how much we need.
        // The header contains a fixed value in the first 12 bytes,
        // and then has an array after that.
        // Arrays have the len as the first UINT32, so we need to read 16 bytes
        inData[0] = ByteBuffer.allocateDirect(16);
        inMessage.setIov(inData);
        
        while( bytesRead != 16 ){
            bytesRead = POSIX.recvmsg(m_fd, inMessage, MSG_PEEK);
            if( bytesRead < 0 ){
                int errno = POSIX.errno();
                throw new IOException( "Unable to receive data: " + POSIX.strerror(errno) );
            }
        }
        
        /* Parse the details from the header */
        byte endian = inData[0].get(0);
        byte type = inData[0].get(1);
        byte protover = inData[0].get(3);
        if (protover > Message.PROTOCOL) {
            //buf = null;
            throw new MessageProtocolVersionException(String.format("Protocol version %s is unsupported", protover));
        }
        
        if( endian == 0x42 /* ASCII 'B' */ ){
            inData[0].order(ByteOrder.BIG_ENDIAN);
        }else if( endian == 0x6C /* ASCII 'l' */ ){
            inData[0].order(ByteOrder.LITTLE_ENDIAN);
        }else{
            logger.error( "Incorrect endian {}", endian );
            throw new IOException( "incorrect endianess" );
        }
        
        IntBuffer asInt = inData[0].asIntBuffer();
        
        //Get the length of the body
        messageBodyLen = asInt.get(1);
            
        // Get the length of the header array
        headerArrayLen = asInt.get(3);
        if (0 != headerArrayLen % 8) {
            headerArrayLen += 8 - (headerArrayLen % 8);
        }
        
        totalMessageLen = 12 + (4 + headerArrayLen) + messageBodyLen;
        
        inData[0] = ByteBuffer.allocateDirect(totalMessageLen);
        inMessage.setIov(inData);
        
        bytesRead = 0;
        
        // Now read the entire message
        while( bytesRead != totalMessageLen ){
            bytesRead = POSIX.recvmsg(m_fd, inMessage, 0);
            if( bytesRead < 0 ){
                int errno = POSIX.errno();
                throw new IOException( "Unable to receive data: " + POSIX.strerror(errno) );
            }
        }
        
        //logger.debug( "total message len {} headerArrayLen {}", totalMessageLen, headerArrayLen);
        byte[] header1 = new byte[12];
        byte[] arrayHeader = new byte[headerArrayLen + 8];
        byte[] body = new byte[messageBodyLen];
        
        inData[0].get(header1);
        // Note: Message.java is assuming that the array length has padding
        // (or something like that) for this message type, so we need to first
        // copy the first 4 bytes, and then the remainder after that.
        inData[0].get(arrayHeader, 0, 4);
        inData[0].get(arrayHeader, 8, arrayHeader.length - 8);
        inData[0].get(body);
        
        try {
            m = MessageFactory.createMessage(type, header1, arrayHeader, body);
        } catch (DBusException dbe) {
            logger.debug("", dbe);
            throw dbe;
        } catch (RuntimeException exRe) { // this really smells badly!
            logger.debug("", exRe);
            throw exRe;
        }
        
        logger.debug("=> {}", m);
        
        return m;
    }

    @Override
    public void close() throws IOException {
        if( m_isClosed ) return;
        m_isClosed = true;
        POSIX.close( m_fd );
    }
    
}
