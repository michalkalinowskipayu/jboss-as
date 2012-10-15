package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
* Test case for testing sequences of management operations.
*
* @author Richard Achmatowicz (c) 2011 Red Hat Inc.
*/
@RunWith(BMUnitRunner.class)
public class OperationSequencesTestCase extends AbstractSubsystemTest {

    static final String SUBSYSTEM_XML_FILE = "subsystem-jgroups-test.xml" ;

    // stack test operations
    static final ModelNode addStackOp = getProtocolStackAddOperation("maximal2");
    // addStackOpWithParams calls the operation  below to check passing optional parameters
    //  /subsystem=jgroups/stack=maximal2:add(transport={type=UDP},protocols=[{type=MPING},{type=FLUSH}])
    static final ModelNode addStackOpWithParams = getProtocolStackAddOperationWithParameters("maximal2");
    static final ModelNode removeStackOp = getProtocolStackRemoveOperation("maximal2");

    // transport test operations
    static final ModelNode addTransportOp = getTransportAddOperation("maximal2", "UDP");
    // addTransportOpWithProps calls the operation below to check passing optional parameters
    //   /subsystem=jgroups/stack=maximal2/transport=UDP:add(properties=[{A=>a},{B=>b}])
    static final ModelNode addTransportOpWithProps = getTransportAddOperationWithProperties("maximal2", "UDP");
    static final ModelNode removeTransportOp = getTransportRemoveOperation("maximal2", "UDP");

    // protocol test operations
    static final ModelNode addProtocolOp = getProtocolAddOperation("maximal2", "MPING");
    // addProtocolOpWithProps calls the operation below to check passing optional parameters
    //   /subsystem=jgroups/stack=maximal2:add-protocol(type=MPING, properties=[{A=>a},{B=>b}])
    static final ModelNode addProtocolOpWithProps = getProtocolAddOperationWithProperties("maximal2", "MPING");
    static final ModelNode removeProtocolOp = getProtocolRemoveOperation("maximal2", "MPING");

    public OperationSequencesTestCase() {
        super(JGroupsExtension.SUBSYSTEM_NAME, new JGroupsExtension());
    }

    @Test
    public void testProtocolStackAddRemoveAddSequence() throws Exception {

        // Parse and install the XML into the controller
        String subsystemXml = getSubsystemXml() ;
        KernelServices servicesA = super.installInController(subsystemXml) ;

        ModelNode[] batchToAddStack = {addStackOp, addTransportOp, addProtocolOp} ;
        ModelNode compositeOp = getCompositeOperation(batchToAddStack);

        // add a protocol stack, its transport and a protocol as a batch
        ModelNode result = servicesA.executeOperation(compositeOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the stack
        result = servicesA.executeOperation(removeStackOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // add the same stack
        result = servicesA.executeOperation(compositeOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
    }

    @Test
    public void testProtocolStackRemoveRemoveSequence() throws Exception {

        // Parse and install the XML into the controller
        String subsystemXml = getSubsystemXml() ;
        KernelServices servicesA = super.installInController(subsystemXml) ;

        ModelNode[] batchToAddStack = {addStackOp, addTransportOp, addProtocolOp} ;
        ModelNode compositeOp = getCompositeOperation(batchToAddStack);

        // add a protocol stack
        ModelNode result = servicesA.executeOperation(compositeOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack
        result = servicesA.executeOperation(removeStackOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack again
        result = servicesA.executeOperation(removeStackOp);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    /*
     * Tests the ability of the /subsystem=jgroups/stack=X:add() operation
     * to correctly process the optional TRANSPORT and PROTOCOLS parameters.
     */
    @Test
    public void testProtocolStackAddRemoveSequenceWithParameters() throws Exception {
        // Parse and install the XML into the controller
        String subsystemXml = getSubsystemXml() ;
        KernelServices servicesA = super.installInController(subsystemXml) ;

        // add a protocol stack specifying TRANSPORT and PROTOCOLS parameters
        ModelNode result = servicesA.executeOperation(addStackOpWithParams);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // check some random values

        // remove the protocol stack
        result = servicesA.executeOperation(removeStackOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack again
        result = servicesA.executeOperation(removeStackOp);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    @Test
    @BMRule(name="Test remove rollback operation",
            targetClass="org.jboss.as.clustering.jgroups.subsystem.ProtocolStackRemove",
            targetMethod="performRuntime",
            targetLocation="AT EXIT",
            action="$1.setRollbackOnly()")
    public void testProtocolStackRemoveRollback() throws Exception {

        // Parse and install the XML into the controller
        String subsystemXml = getSubsystemXml() ;
        KernelServices servicesA = super.installInController(subsystemXml) ;

        ModelNode[] batchToAddStack = {addStackOp, addTransportOp, addProtocolOp} ;
        ModelNode compositeOp = getCompositeOperation(batchToAddStack);

        // add a protocol stack
        ModelNode result = servicesA.executeOperation(compositeOp);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        // remove the protocol stack
        // the remove has OperationContext.setRollbackOnly() injected
        // and so is expected to fail
        result = servicesA.executeOperation(removeStackOp);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());

        // need to check that all services are correctly re-installed
        ServiceName channelFactoryServiceName = ChannelFactoryService.getServiceName("maximal2");
        Assert.assertNotNull("channel factory service not installed", servicesA.getContainer().getService(channelFactoryServiceName));
    }

    private static ModelNode getCompositeOperation(ModelNode[] operations) {
        // create the address of the cache
        ModelNode compositeOp = new ModelNode() ;
        compositeOp.get(OP).set(COMPOSITE);
        compositeOp.get(OP_ADDR).setEmptyList();
        // the operations to be performed
        for (ModelNode operation : operations) {
            compositeOp.get(STEPS).add(operation);
        }

        return compositeOp ;
    }

    private static ModelNode getSubsystemAddOperation() {
        // create the address of the subsystem
        PathAddress subsystemAddress =  PathAddress.pathAddress(
                PathElement.pathElement(SUBSYSTEM, JGroupsExtension.SUBSYSTEM_NAME));

        ModelNode addOp = new ModelNode() ;
        addOp.get(OP).set(ADD);
        addOp.get(OP_ADDR).set(subsystemAddress.toModelNode());
        // required attributes
        addOp.get(ModelKeys.DEFAULT_STACK).set("maximal2");

        return addOp ;
    }

    private static ModelNode getProtocolStackAddOperation(String stackName) {
        // create the address of the cache
        PathAddress stackAddr = getProtocolStackAddress(stackName);
        ModelNode addOp = new ModelNode() ;
        addOp.get(OP).set(ADD);
        addOp.get(OP_ADDR).set(stackAddr.toModelNode());
        // required attributes
        // addOp.get(DEFAULT_CACHE).set("default");

        return addOp ;
    }

    private static ModelNode getProtocolStackAddOperationWithParameters(String stackName) {

        ModelNode addOp = getProtocolStackAddOperation(stackName);

        // add optional TRANSPORT attribute
        ModelNode transport = new ModelNode();
        transport.get(ModelKeys.TYPE).set("UDP");
        addOp.get(ModelKeys.TRANSPORT).set(transport);

        // add optional PROTOCOLS attribute
        ModelNode protocolsList = new ModelNode();

        ModelNode mping = new ModelNode() ;
        mping.get(ModelKeys.TYPE).set("MPING");
        protocolsList.add(mping);

        ModelNode flush = new ModelNode() ;
        flush.get(ModelKeys.TYPE).set("pbcast.FLUSH");
        protocolsList.add(flush);

        addOp.get(ModelKeys.PROTOCOLS).set(protocolsList);

        return addOp ;
    }


    private static ModelNode getProtocolStackRemoveOperation(String stackName) {
        // create the address of the cache
        PathAddress stackAddr = getProtocolStackAddress(stackName);
        ModelNode removeOp = new ModelNode() ;
        removeOp.get(OP).set(REMOVE);
        removeOp.get(OP_ADDR).set(stackAddr.toModelNode());

        return removeOp ;
    }

    private static ModelNode getTransportAddOperation(String stackName, String protocolType) {
        // create the address of the cache
        PathAddress transportAddr = getTransportAddress(stackName);
        ModelNode addOp = new ModelNode() ;
        addOp.get(OP).set(ADD);
        addOp.get(OP_ADDR).set(transportAddr.toModelNode());
        // required attributes
        addOp.get(ModelKeys.TYPE).set(protocolType);

        return addOp ;
    }

    private static ModelNode getTransportAddOperationWithProperties(String stackName, String protocolType) {

        ModelNode addOp = getTransportAddOperation(stackName, protocolType);

        // add optional PROPERTIES attribute
        ModelNode propertyList = new ModelNode();

        ModelNode propA = new ModelNode();
        propA.add("A","a");
        propertyList.add(propA);

        ModelNode propB = new ModelNode();
        propB.add("B","b");
        propertyList.add(propB);

        addOp.get(ModelKeys.PROPERTIES).set(propertyList);

        return addOp ;
    }



    private static ModelNode getTransportRemoveOperation(String stackName, String protocolType) {
        // create the address of the cache
        PathAddress transportAddr = getTransportAddress(stackName);
        ModelNode removeOp = new ModelNode() ;
        removeOp.get(OP).set(REMOVE);
        removeOp.get(OP_ADDR).set(transportAddr.toModelNode());

        return removeOp ;
    }

    private static ModelNode getProtocolAddOperation(String stackName, String protocolType) {
        // create the address of the cache
        PathAddress stackAddr = getProtocolStackAddress(stackName);
        ModelNode addOp = new ModelNode() ;
        addOp.get(OP).set("add-protocol");
        addOp.get(OP_ADDR).set(stackAddr.toModelNode());
        // required attributes
        addOp.get(ModelKeys.TYPE).set(protocolType);

        return addOp ;
    }

    private static ModelNode getProtocolAddOperationWithProperties(String stackName, String protocolType) {

        ModelNode addOp = getProtocolAddOperation(stackName, protocolType);

        // add optional PROPERTIES attribute
        ModelNode propertyList = new ModelNode();

        ModelNode propA = new ModelNode();
        propA.add("A","a");
        propertyList.add(propA);

        ModelNode propB = new ModelNode();
        propB.add("B","b");
        propertyList.add(propB);

        addOp.get(ModelKeys.PROPERTIES).set(propertyList);

        return addOp ;
    }


    private static ModelNode getProtocolRemoveOperation(String stackName, String protocolType) {
        // create the address of the cache
        PathAddress stackAddr = getProtocolStackAddress(stackName);
        ModelNode removeOp = new ModelNode() ;
        removeOp.get(OP).set("remove-protocol");
        removeOp.get(OP_ADDR).set(stackAddr.toModelNode());
        // required attributes
        removeOp.get(ModelKeys.TYPE).set(protocolType);

        return removeOp ;
    }

    private static PathAddress getProtocolStackAddress(String stackName) {
        // create the address of the stack
        PathAddress stackAddr = PathAddress.pathAddress(
                PathElement.pathElement(SUBSYSTEM, JGroupsExtension.SUBSYSTEM_NAME),
                PathElement.pathElement("stack",stackName));
        return stackAddr ;
    }

    private static PathAddress getTransportAddress(String stackName) {
        // create the address of the cache
        PathAddress protocolAddr = PathAddress.pathAddress(
                PathElement.pathElement(SUBSYSTEM, JGroupsExtension.SUBSYSTEM_NAME),
                PathElement.pathElement("stack",stackName),
                PathElement.pathElement("transport", "TRANSPORT"));
        return protocolAddr ;
    }

    private static PathAddress getProtocolAddress(String stackName, String protocolType) {
        // create the address of the cache
        PathAddress protocolAddr = PathAddress.pathAddress(
                PathElement.pathElement(SUBSYSTEM, JGroupsExtension.SUBSYSTEM_NAME),
                PathElement.pathElement("stack",stackName),
                PathElement.pathElement("protocol", protocolType));
        return protocolAddr ;
    }

    private String getSubsystemXml() throws IOException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(SUBSYSTEM_XML_FILE);
        if (url == null) {
            throw new IllegalStateException(String.format("Failed to locate %s", SUBSYSTEM_XML_FILE));
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(url.toURI())));
            StringWriter writer = new StringWriter();
            try {
                String line = reader.readLine();
                while (line != null) {
                    writer.write(line);
                    line = reader.readLine();
                }
            } finally {
                reader.close();
            }
            return writer.toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}