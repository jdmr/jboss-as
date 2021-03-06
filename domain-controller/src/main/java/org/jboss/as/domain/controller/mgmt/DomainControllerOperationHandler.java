/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller.mgmt;

import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.FileRepository;
import org.jboss.as.domain.controller.ServerManagerClient;
import org.jboss.as.model.DeploymentUnitElement;
import org.jboss.as.protocol.ProtocolUtils;
import org.jboss.as.protocol.mgmt.AbstractMessageHandler;
import org.jboss.as.protocol.ByteDataInput;
import org.jboss.as.protocol.ByteDataOutput;
import org.jboss.as.protocol.Connection;
import org.jboss.as.protocol.mgmt.ManagementOperationHandler;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementResponse;
import org.jboss.as.protocol.SimpleByteDataInput;
import org.jboss.as.protocol.SimpleByteDataOutput;
import org.jboss.as.protocol.StreamUtils;
import static org.jboss.as.protocol.StreamUtils.readUTFZBytes;
import static org.jboss.as.protocol.StreamUtils.safeClose;

import static org.jboss.as.protocol.ProtocolUtils.expectHeader;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshaller;
import static org.jboss.marshalling.Marshalling.createByteOutput;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * {@link org.jboss.as.protocol.mgmt.ManagementOperationHandler} implementation used to handle request
 * intended for the domain controller.
 *
 * @author John Bailey
 */
public class  DomainControllerOperationHandler extends AbstractMessageHandler implements ManagementOperationHandler, Service<DomainControllerOperationHandler> {
    private static final Logger log = Logger.getLogger("org.jboss.as.management");
    public static final ServiceName SERVICE_NAME = DomainController.SERVICE_NAME.append("operation", "handler");

    private final InjectedValue<DomainController> domainControllerValue = new InjectedValue<DomainController>();
    private final InjectedValue<ScheduledExecutorService> executorServiceValue = new InjectedValue<ScheduledExecutorService>();
    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<ThreadFactory>();
    private final InjectedValue<FileRepository> localFileRepositoryValue = new InjectedValue<FileRepository>();

    private DomainController domainController;
    private ScheduledExecutorService executorService;
    private ThreadFactory threadFactory;
    private FileRepository localFileRepository;

    /** {@inheritDoc} */
    public final byte getIdentifier() {
        return DomainControllerProtocol.DOMAIN_CONTROLLER_REQUEST;
    }

    /** {@inheritDoc} */
    public synchronized void start(StartContext context) throws StartException {
        try {
            domainController = domainControllerValue.getValue();
            executorService = executorServiceValue.getValue();
            localFileRepository = localFileRepositoryValue.getValue();
            this.threadFactory = threadFactoryValue.getValue();
        } catch (IllegalStateException e) {
            throw new StartException(e);
        }
    }

    /** {@inheritDoc} */
    public synchronized void stop(StopContext context) {
        domainController = null;
        executorService = null;
        localFileRepository = null;
    }

    /** {@inheritDoc} */
    public synchronized DomainControllerOperationHandler getValue() throws IllegalStateException {
        return this;
    }

    public Injector<DomainController> getDomainControllerInjector() {
        return domainControllerValue;
    }

    public Injector<ScheduledExecutorService> getExecutorServiceInjector() {
        return executorServiceValue;
    }

    public Injector<FileRepository> getLocalFileRepositoryInjector() {
        return localFileRepositoryValue;
    }

    public Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    /**
     * Handles the request.  Reads the requested command byte. Once the command is available it will get the
     * appropriate operation and execute it.
     *
     * @param connection  The connection
     * @param input The connection input
     * @throws IOException If any problems occur performing the operation
     */
    @Override
    public void handle(final Connection connection, final InputStream input) throws IOException {
        final byte commandCode;
        expectHeader(input, ManagementProtocol.REQUEST_OPERATION);
        commandCode = StreamUtils.readByte(input);

        final AbstractMessageHandler operation = operationFor(commandCode);
        if (operation == null) {
            throw new IOException("Invalid command code " + commandCode + " received from server manager");
        }
        log.debugf("Received DomainController operation [%s]", operation);

        operation.handle(connection, input);
    }

    private AbstractMessageHandler operationFor(final byte commandByte) {
        switch (commandByte) {
            case DomainControllerProtocol.REGISTER_REQUEST:
                return new RegisterOperation();
            case DomainControllerProtocol.SYNC_FILE_REQUEST:
                return new GetFileOperation();
            case DomainControllerProtocol.UNREGISTER_REQUEST:
                return new UnregisterOperation();
            default: {
                return null;
            }
        }
    }

    private abstract class DomainControllerOperation extends ManagementResponse {
        @Override
        protected void readRequest(final InputStream input) throws IOException {
            super.readRequest(input);
            final String serverManagerId;
            expectHeader(input, DomainControllerProtocol.PARAM_SERVER_MANAGER_ID);
            serverManagerId = readUTFZBytes(input);
            readRequest(serverManagerId, input);
        }

        protected abstract void readRequest(final String serverManagerId, final InputStream input) throws IOException;
    }

    private class RegisterOperation extends DomainControllerOperation {
        @Override
        protected final byte getResponseCode() {
            return DomainControllerProtocol.REGISTER_RESPONSE;
        }

        @Override
        protected final void readRequest(final String serverManagerId, final InputStream inputStream) throws IOException {
            ByteDataInput input = null;
            try {
                input = new SimpleByteDataInput(inputStream);
                expectHeader(input, DomainControllerProtocol.PARAM_SERVER_MANAGER_HOST);
                final int addressSize = input.readInt();
                byte[] addressBytes = new byte[addressSize];
                input.readFully(addressBytes);
                expectHeader(input, DomainControllerProtocol.PARAM_SERVER_MANAGER_PORT);
                final int port = input.readInt();
                final InetAddress address = InetAddress.getByAddress(addressBytes);
                final ServerManagerClient client = new RemoteDomainControllerClient(serverManagerId, address, port, executorService, threadFactory);
                domainController.addClient(client);
                log.infof("Server manager registered [%s]", client);
            } finally {
                safeClose(input);
            }
        }

        @Override
        protected final void sendResponse(final OutputStream output) throws IOException {
            final Marshaller marshaller = getMarshaller();
            marshaller.start(createByteOutput(output));
            marshaller.writeByte(DomainControllerProtocol.PARAM_DOMAIN_MODEL);
            marshaller.writeObject(domainController.getDomainModel());
            marshaller.finish();
        }
    }

    private class UnregisterOperation extends DomainControllerOperation {
        @Override
        protected final byte getResponseCode() {
            return DomainControllerProtocol.UNREGISTER_RESPONSE;
        }

        @Override
        protected final void readRequest(final String serverManagerId, final InputStream input) throws IOException {
            log.infof("Server manager unregistered [%s]", serverManagerId);
            domainController.removeClient(serverManagerId);
        }
    }

    private class GetFileOperation extends DomainControllerOperation {
        private File localPath;

        @Override
        protected final byte getResponseCode() {
            return DomainControllerProtocol.SYNC_FILE_RESPONSE;
        }

        @Override
        protected final void readRequest(final String serverManagerId, final InputStream inputStream) throws IOException {
            final byte rootId;
            final String filePath;
            ByteDataInput input = null;
            try {
                input = new SimpleByteDataInput(inputStream);
                expectHeader(input, DomainControllerProtocol.PARAM_ROOT_ID);
                rootId = input.readByte();
                expectHeader(input, DomainControllerProtocol.PARAM_FILE_PATH);
                filePath = input.readUTF();

                log.debugf("Server manager [%s] requested file [%s] from root [%d]", serverManagerId, filePath, rootId);
                switch (rootId) {
                    case (byte)DomainControllerProtocol.PARAM_ROOT_ID_FILE: {
                        localPath = localFileRepository.getFile(filePath);
                        break;
                    }
                    case (byte)DomainControllerProtocol.PARAM_ROOT_ID_CONFIGURATION: {
                        localPath = localFileRepository.getConfigurationFile(filePath);
                        break;
                    }
                    case (byte)DomainControllerProtocol.PARAM_ROOT_ID_DEPLOYMENT: {
                        byte[] hash = DeploymentUnitElement.hexStringToBytes(filePath);
                        localPath = localFileRepository.getDeploymentRoot(hash);
                        break;
                    }
                    default: {
                        throw new IOException(String.format("Invalid root id [%d]", rootId));
                    }
                }
            } finally {
                safeClose(input);
            }
        }

        @Override
        protected void sendResponse(final OutputStream outputStream) throws IOException {
            ByteDataOutput output = null;
            try {
                output = new SimpleByteDataOutput(outputStream);
                output.writeByte(DomainControllerProtocol.PARAM_NUM_FILES);
                if (localPath == null || !localPath.exists()) {
                    output.writeInt(-1);
                } else if (localPath.isFile()) {
                    output.writeInt(1);
                    writeFile(localPath, output);
                } else {
                    final List<File> childFiles = getChildFiles(localPath);
                    output.writeInt(childFiles.size());
                    for (File child : childFiles) {
                        writeFile(child, output);
                    }
                }
                output.close();
            } finally {
                safeClose(output);
            }
        }

        private List<File> getChildFiles(final File base) {
            final List<File> childFiles = new ArrayList<File>();
            getChildFiles(base, childFiles);
            return childFiles;
        }

        private void getChildFiles(final File base, final List<File> childFiles) {
            for (File child : base.listFiles()) {
                if (child.isFile()) {
                    childFiles.add(child);
                } else {
                    getChildFiles(child, childFiles);
                }
            }
        }

        private String getRelativePath(final File parent, final File child) {
            return child.getAbsolutePath().substring(parent.getAbsolutePath().length());
        }

        private void writeFile(final File file, final DataOutput output) throws IOException {
            output.writeByte(DomainControllerProtocol.FILE_START);
            output.writeByte(DomainControllerProtocol.PARAM_FILE_PATH);
            output.writeUTF(getRelativePath(localPath, file));
            output.writeByte(DomainControllerProtocol.PARAM_FILE_SIZE);
            output.writeLong(file.length());
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            output.writeByte(DomainControllerProtocol.FILE_END);
            log.infof("Wrote file [%s]", file);
        }
    }

    private static Marshaller getMarshaller() throws IOException {
        return ProtocolUtils.getMarshaller(ProtocolUtils.MODULAR_CONFIG);
    }
}
