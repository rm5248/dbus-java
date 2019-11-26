package org.freedesktop.dbus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import org.freedesktop.dbus.FileDescriptor;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MarshallingFileDescriptorTest {

    private static final String TEST_OBJECT_PATH = "/TestFileDescriptor";
    private static final String TEST_BUSNAME = "foo.bar.TestFileDescriptor";
    
    private DBusConnection serverConn;
    private DBusConnection clientConn;
    
    private FileInputStream sampleFileStream;
    
    @BeforeEach
    public void before() throws DBusException, FileNotFoundException, IOException {
        serverConn = DBusConnection.getConnection(DBusBusType.SESSION);
        clientConn = DBusConnection.getConnection(DBusBusType.SESSION);
        serverConn.setWeakReferences(true);
        clientConn.setWeakReferences(true);
        serverConn.requestBusName(TEST_BUSNAME);

        sampleFileStream = new FileInputStream(File.createTempFile("dbustest", "testFd"));
        
        FileDescriptor tosend = new FileDescriptor(sampleFileStream.getFD());
        GetFileDescriptor fd = new GetFileDescriptor(tosend);
         
        System.out.println("Created file descriptor: " + tosend.getIntFileDescriptor());
        
        serverConn.exportObject(TEST_OBJECT_PATH, fd);
    }

    @AfterEach
    public void after() throws IOException {
        DBusExecutionException dbee = serverConn.getError();
        if (null != dbee) {
            throw dbee;
        }
        dbee = clientConn.getError();
        if (null != dbee) {
            throw dbee;
        }
        
        clientConn.disconnect();
        serverConn.disconnect();
        sampleFileStream.close();
    }
    
    @Test
    public void testFileDescriptor() throws DBusException, IOException {
        DBusInterface remoteObject = clientConn.getRemoteObject("foo.bar.TestFileDescriptor", TEST_OBJECT_PATH, IFileDescriptor.class);

        assertTrue(remoteObject instanceof IFileDescriptor, "Expected instance of GetFileDescriptor");
        
        FileDescriptor fileDescriptor = ((IFileDescriptor) remoteObject).getFileDescriptor();
        assertNotNull(fileDescriptor, "Descriptor should not be null");
        
        //assertTrue(fileDescriptor.valid(), "Descriptor has to be valid");
        int receivedFdId = fileDescriptor.getIntFileDescriptor();
        System.out.println("Received file descriptor with ID: " + receivedFdId);
        assertNotEquals(new FileDescriptor(sampleFileStream.getFD()).getIntFileDescriptor(), receivedFdId);
    }
    
    // ==================================================================================================

    public static class GetFileDescriptor implements IFileDescriptor {

        private final FileDescriptor fileDescriptor;

        public GetFileDescriptor(FileDescriptor _descriptor) {
            fileDescriptor = _descriptor;
        }
        
        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public String getObjectPath() {
            return null;
        }

        @Override
        public FileDescriptor getFileDescriptor() {
            return fileDescriptor;
        }
        
    }
    
    @DBusInterfaceName("foo.bar.TestFileDescriptor")
    public static interface IFileDescriptor extends DBusInterface {

        FileDescriptor getFileDescriptor();
    }
}
