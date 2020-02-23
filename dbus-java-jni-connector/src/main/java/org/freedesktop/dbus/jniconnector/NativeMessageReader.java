package org.freedesktop.dbus.jniconnector;

import java.io.IOException;
import jnr.posix.POSIXFactory;
import org.freedesktop.dbus.MessageReader;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a MessageReader that uses JNI code to call `sendmsg`.
 */
public class NativeMessageReader implements MessageReader {

    private static jnr.posix.POSIX POSIX = POSIXFactory.getPOSIX();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final Logger logger_native = LoggerFactory.getLogger( NativeMessageReader.class.getName() + ".native" );

    private int m_fd;
    private boolean m_isClosed;
    private int m_nativeHandle;

    public NativeMessageReader( int fd ){
        m_fd = fd;
        m_isClosed = false;
        m_nativeHandle = openNativeHandle( m_fd );
    }

    @Override
    public boolean isClosed() {
        return m_isClosed;
    }

    @Override
    public Message readMessage() throws IOException, DBusException {
        MsgHdr h = readNative( m_nativeHandle );

        return null;
    }

    @Override
    public void close() throws IOException {
        if( m_isClosed ) return;
        m_isClosed = true;
        POSIX.close( m_fd );
        closeNativeHandle( m_nativeHandle );
    }

    /**
     * Given a filedescriptor, return a native handle to native data.
     * @param fd
     * @return
     */
    private native int openNativeHandle( int fd );

    private native void closeNativeHandle( int handle );

    private native MsgHdr readNative( int handle ) throws IOException;

}
