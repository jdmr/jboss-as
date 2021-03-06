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

/**
 *
 */
package org.jboss.as.server.manager;

import javax.xml.namespace.QName;

import org.jboss.as.model.Element;
import org.jboss.as.model.ModelXmlParsers;
import org.jboss.as.model.Namespace;
import org.jboss.modules.ModuleLoadException;
import org.jboss.staxmapper.XMLMapper;

/**
 * A {@link StandardElementReaderRegistrar} that uses a static list of extensions.
 *
 * @author Brian Stansberry
 */
public class StandardElementReaderRegistrarImpl implements StandardElementReaderRegistrar {

    /* (non-Javadoc)
     * @see org.jboss.as.server.manager.ElementHandlerRegistrar#registerStandardDomainHandlers(org.jboss.staxmapper.XMLMapper)
     */
    @Override
    public synchronized void registerStandardDomainReaders(XMLMapper mapper) throws ModuleLoadException {

        for (Namespace ns : Namespace.STANDARD_NAMESPACES) {
            mapper.registerRootElement(new QName(ns.getUriString(), Element.DOMAIN.getLocalName()), ModelXmlParsers.DOMAIN_XML_READER);
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.as.server.manager.ElementHandlerRegistrar#registerStandardHostHandlers(org.jboss.staxmapper.XMLMapper)
     */
    @Override
    public synchronized void registerStandardHostReaders(XMLMapper mapper) throws ModuleLoadException {

        for (Namespace ns : Namespace.STANDARD_NAMESPACES) {
            mapper.registerRootElement(new QName(ns.getUriString(), Element.HOST.getLocalName()), ModelXmlParsers.HOST_XML_READER);
        }
    }
}
