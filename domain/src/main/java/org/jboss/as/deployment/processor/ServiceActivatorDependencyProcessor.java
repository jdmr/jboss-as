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

package org.jboss.as.deployment.processor;

import org.jboss.as.deployment.DeploymentPhases;
import org.jboss.as.deployment.attachment.VirtualFileAttachment;
import org.jboss.as.deployment.module.ModuleConfig;
import org.jboss.as.deployment.module.ModuleDependencies;
import org.jboss.as.deployment.unit.DeploymentUnitContext;
import org.jboss.as.deployment.unit.DeploymentUnitProcessingException;
import org.jboss.as.deployment.unit.DeploymentUnitProcessor;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment processor that adds required dependencies for executing service activators.
 *
 * @author John Bailey
 */
public class ServiceActivatorDependencyProcessor implements DeploymentUnitProcessor {
    public static final long PRIORITY = DeploymentPhases.PARSE_DESCRIPTORS.plus(300L);
    private static final String SERVICE_ACTIVATOR_PATH = "META-INF/services/" + ServiceActivator.class.getName();
    private static final ModuleConfig.Dependency MSC_DEP = new ModuleConfig.Dependency(ModuleIdentifier.create("org.jboss.msc"), true, false, false);

    /**
     * Add the dependencies if the deployment contains a service activator loader entry.
     * @param context the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void processDeployment(DeploymentUnitContext context) throws DeploymentUnitProcessingException {
        final VirtualFile deploymentRoot = VirtualFileAttachment.getVirtualFileAttachment(context);
        if(deploymentRoot == null)
            return;
        if(deploymentRoot.getChild(SERVICE_ACTIVATOR_PATH).exists()) {
            context.putAttachment(ServiceActivatorMarker.ATTACHMENT_KEY, new ServiceActivatorMarker());
            ModuleDependencies.addDependency(context, MSC_DEP);
        }
    }
}
