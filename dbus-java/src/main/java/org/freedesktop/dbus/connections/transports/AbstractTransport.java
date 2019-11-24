package org.freedesktop.dbus.connections.transports;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.freedesktop.dbus.FDMessageWriter;

import org.freedesktop.dbus.MessageReader;
import org.freedesktop.dbus.MessageWriter;
import org.freedesktop.dbus.OutputStreamMessageWriter;
import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.SASL;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all transport types.
 * 
 * @author hypfvieh
 * @since v3.2.0 - 2019-02-08
 */
public abstract class AbstractTransport implements Closeable {

    private final Logger     logger;
    private final BusAddress address;

    private SASL.SaslMode    saslMode;

    private int              saslAuthMode;
    private MessageReader    inputReader;
    private MessageWriter    outputWriter;
    
    private boolean fileDescriptorSupported;

    AbstractTransport(BusAddress _address) {
        address = _address;
        
        if (_address.isListeningSocket()) {
            saslMode = SASL.SaslMode.SERVER;    
        } else {
            saslMode = SASL.SaslMode.CLIENT;
        }
        
        saslAuthMode = SASL.AUTH_NONE;
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Write a message to the underlying socket.
     * 
     * @param _msg message to write
     * @throws IOException on write error or if output was already closed or null
     */
    public void writeMessage(Message _msg) throws IOException {
//        if (!fileDescriptorSupported && Message.ArgumentType.FILEDESCRIPTOR == _msg.getType()) {
//            throw new IllegalArgumentException("File descriptors are not supported by the remote end!");
//        }
        if (outputWriter != null && !outputWriter.isClosed()) {
            outputWriter.writeMessage(_msg);
        } else {
            throw new IOException("OutputWriter already closed or null");
        }
    }
    
    /**
     * Read a message from the underlying socket.
     * 
     * @return read message, maybe null
     * @throws IOException when input already close or null
     * @throws DBusException when message could not be converted to a DBus message
     */
    public Message readMessage() throws IOException, DBusException {
        if (inputReader != null && !inputReader.isClosed()) {
            return inputReader.readMessage();
        }
        throw new IOException("InputReader already closed or null");
    }
    
    /**
     * Abstract method implemented by concrete sub classes to establish a connection 
     * using whatever transport type (e.g. TCP/Unix socket).
     * @throws IOException when connection fails
     */
    abstract void connect() throws IOException;
    
    /**
     * Method to indicate if passing of file descriptors is allowed.
     *  
     * @return true to allow FD passing, false otherwise
     */
    abstract boolean hasFileDescriptorSupport();
    
    /**
     * Helper method to authenticate to DBus using SASL.
     * 
     * @param _out output stream
     * @param _in input stream
     * @param _sock socket
     * @throws IOException on any error
     */
    protected void authenticate(OutputStream _out, InputStream _in, Socket _sock) throws IOException {
        SASL sasl = new SASL(hasFileDescriptorSupport());
        if (!sasl.auth(saslMode, saslAuthMode, address.getGuid(), _out, _in, _sock)) {
            _out.close();
            throw new IOException("Failed to auth");
        }
        fileDescriptorSupported = sasl.isFileDescriptorSupported();
    }


    protected void setOutputWriter(OutputStream _outputStream) {
        outputWriter = new OutputStreamMessageWriter(_outputStream);        
    }
    
    protected void setOutputFD(int _fd){
        outputWriter = new FDMessageWriter(_fd);
    }

    protected void setInputReader(InputStream _inputStream) {
        inputReader = new MessageReader(_inputStream);
    }
    
    protected int getSaslAuthMode() {
        return saslAuthMode;
    }

    protected void setSaslAuthMode(int _saslAuthMode) {
        saslAuthMode = _saslAuthMode;
    }

    protected SASL.SaslMode getSaslMode() {
        return saslMode;
    }

    protected void setSaslMode(SASL.SaslMode _saslMode) {
        saslMode = _saslMode;
    }

    
    
    protected BusAddress getAddress() {
        return address;
    }

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void close() throws IOException {
        inputReader.close();
        outputWriter.close();
    }
    
}
