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

package org.jboss.as.model;

/**
 * @author Emanuel Muckenhuber
 */
public class ServerElementSystemPropertyUpdate extends AbstractModelUpdate<ServerElement, Void> {

    private static final long serialVersionUID = -2162643350016256639L;
    private final AbstractPropertyUpdate update;

    public ServerElementSystemPropertyUpdate(AbstractPropertyUpdate update) {
        this.update = update;
    }

    /** {@inheritDoc} */
    @Override
    public Class<ServerElement> getModelElementType() {
        return ServerElement.class;
    }

    /** {@inheritDoc} */
    @Override
    protected void applyUpdate(ServerElement element) throws UpdateFailedException {
        update.applyUpdate(element.getSystemProperties());
    }

    /** {@inheritDoc} */
    @Override
    public ServerElementSystemPropertyUpdate getCompensatingUpdate(ServerElement original) {
        return new ServerElementSystemPropertyUpdate(update.getCompensatingUpdate(original.getSystemProperties()));
    }

    @Override
    protected ServerSystemPropertyUpdate getServerModelUpdate() {
        return new ServerSystemPropertyUpdate(update);
    }

}
