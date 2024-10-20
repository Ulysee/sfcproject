/*
 * Copyright (c) 2016, 2017 Ericsson Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sfc.genius.util;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.RpcConsumerRegistry;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlanGpe;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class centralizes all Genius RPC accesses which SFC openflow renderer
 * needs when using logical SFFs.
 *
 * @author Diego Granados (diego.jesus.granados.lopez@ericsson.com)
 */
public class SfcGeniusRpcClient {

    private static final Logger LOG = LoggerFactory.getLogger(SfcGeniusRpcClient.class);
    private final ItmRpcService itmRpcService;
    private final OdlInterfaceRpcService ifmRpcService;

    /**
     * Constructor.
     *
     * @param rpcRegistry
     *            The registry used to retrieve RPC services
     */
    public SfcGeniusRpcClient(RpcConsumerRegistry rpcRegistry) {
        LOG.debug("SfcGeniusRpcClient: starting");
        Preconditions.checkNotNull(rpcRegistry);
        itmRpcService = rpcRegistry.getRpcService(ItmRpcService.class);
        ifmRpcService = rpcRegistry.getRpcService(OdlInterfaceRpcService.class);
    }

    /**
     * Retrieve egress actions from Genius.
     *
     * @param targetInterfaceName
     *            the interface to use
     * @param interfaceIsPartOfTheTransportZone
     *            true when the interface is part of the transport zone (i.e. it
     *            is an interface between switching elements in different
     *            compute nodes); false when it is the Neutron interface of a SF
     * @param actionOffset
     *            offsets the order parameter of the actions gotten from genius
     *            RPC
     * @return The egress instructions to use, or empty when the RPC invocation
     *         failed
     */
    public Optional<List<Action>> getEgressActionsFromGeniusRPC(String targetInterfaceName,
            boolean interfaceIsPartOfTheTransportZone, int actionOffset) {

        Optional<List<Action>> result = Optional.empty();
        boolean successful = false;

        LOG.debug("getEgressActionsFromGeniusRPC: starting (target interface={} in the transport zone:{})",
                targetInterfaceName, interfaceIsPartOfTheTransportZone);
        GetEgressActionsForInterfaceInputBuilder builder = new GetEgressActionsForInterfaceInputBuilder()
                .setIntfName(targetInterfaceName).setActionKey(actionOffset);
        if (interfaceIsPartOfTheTransportZone) {
            builder.setTunnelKey((long) SfcGeniusConstants.SFC_VNID);
        }

        GetEgressActionsForInterfaceInput input = builder.build();
        try {
            OdlInterfaceRpcService service = getIfmRpcService();
            if (service != null) {
                RpcResult<GetEgressActionsForInterfaceOutput> output = service.getEgressActionsForInterface(input)
                        .get();
                if (output.isSuccessful()) {
                    result = Optional.of(output.getResult().getAction());
                    LOG.debug("getEgressInstructionsFromGeniusRPC({}) succeeded", input);
                    successful = true;
                } else {
                    LOG.error("getEgressInstructionsFromGeniusRPC({}) failed", input);
                }
            } else {
                LOG.error("getEgressInstructionsFromGeniusRPC({}) failed (service couldn't be retrieved)", input);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("failed to retrieve egress instructions for input {}: ", input, e);
        }
        if (!successful) {
            result = Optional.empty();
        }
        return result;
    }

    /**
     * Given a pair of data plane node identifiers, the method returns the
     * interface to use for sending traffic from the first dpn to the second.
     * This method assumes that a Genius' transport zone exists and that it
     * including all the dataplane nodes involved in the SFC chain, so vxlan-gpe
     * tunnels exist beforehand between all data plane nodes.
     *
     * @param srcDpid
     *            DPN ID for the source dataplane node
     * @param dstDpid
     *            DPN ID for the target dataplane node
     * @return The interface to use for traffic steering between the given
     *         dataplane nodes(empty when some problem arises during the
     *         retrieval)
     */
    public Optional<String> getTargetInterfaceFromGeniusRPC(DpnIdType srcDpid, DpnIdType dstDpid) {
        LOG.debug("getTargetInterfaceFromGeniusRPC: starting (src dpnid:{} dst dpnid:{})", srcDpid, dstDpid);

        final ItmRpcService service = getItmRpcService();
        if (service == null) {
            LOG.error("getTargetInterfaceFromGeniusRPC failed (service couldn't be retrieved)");
            return Optional.empty();
        }

        GetTunnelInterfaceNameInputBuilder builder = new GetTunnelInterfaceNameInputBuilder();
        builder.setSourceDpid(srcDpid.getValue());
        builder.setDestinationDpid(dstDpid.getValue());
        Optional<String> interfaceName;
        RpcResult<GetTunnelInterfaceNameOutput> output;

        try {
            // Try first a specific VxlanGpe interface type
            builder.setTunnelType(TunnelTypeVxlanGpe.class);
            output = service.getTunnelInterfaceName(builder.build()).get();
            interfaceName = Optional.ofNullable(output)
                    .map(RpcResult::getResult)
                    .map(GetTunnelInterfaceNameOutput::getInterfaceName);
            if (output.isSuccessful() && interfaceName.isPresent()) {
                LOG.debug("getTargetInterfaceFromGeniusRPC found VxlanGpe interface {}", interfaceName);
                return interfaceName;
            }

            // If not, try with standard vxlan type, it might also have gpe enabled
            builder.setTunnelType(TunnelTypeVxlan.class);
            output = service.getTunnelInterfaceName(builder.build()).get();
            interfaceName = Optional.ofNullable(output)
                    .map(RpcResult::getResult)
                    .map(GetTunnelInterfaceNameOutput::getInterfaceName);
            if (output.isSuccessful() && interfaceName.isPresent()) {
                LOG.debug("getTargetInterfaceFromGeniusRPC found Vxlan interface {}", interfaceName);
                return interfaceName;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getTargetInterfaceFromGeniusRPC exception when trying to retrieve target interface name: ", e);
        }
        LOG.debug("getTargetInterfaceFromGeniusRPC did not find target interface name");
        return Optional.empty();
    }

    /**
     * Given a Neutron interface to which a VM (hosting a SF instance) is
     * attached, the method returns the DPN ID for the dataplane node in the
     * compute node where the VM is running.
     *
     * @param logicalInterfaceName
     *            the Neutron interface that the SF is attached to
     * @return the DPN ID for the dataplane node in the compute node hosting the
     *         SF, or empty when the value cannot be retrieved
     */
    public Optional<DpnIdType> getDpnIdFromInterfaceNameFromGeniusRPC(String logicalInterfaceName) {
        Optional<DpnIdType> dpnid = Optional.empty();
        boolean successful = false;

        LOG.debug("getDpnIdFromInterfaceNameFromGeniusRPC: starting (logical interface={})", logicalInterfaceName);
        GetDpidFromInterfaceInputBuilder builder = new GetDpidFromInterfaceInputBuilder();
        builder.setIntfName(logicalInterfaceName);
        GetDpidFromInterfaceInput input = builder.build();

        try {
            OdlInterfaceRpcService service = getIfmRpcService();
            if (service != null) {
                LOG.debug("getDpnIdFromInterfaceNameFromGeniusRPC: service is not null, invoking rpc");
                RpcResult<GetDpidFromInterfaceOutput> output = service.getDpidFromInterface(input).get();
                if (output.isSuccessful()) {
                    dpnid = Optional.of(new DpnIdType(output.getResult().getDpid()));
                    LOG.debug("getDpnIdFromInterfaceNameFromGeniusRPC({}) succeeded: {}", input, output);
                    successful = true;
                } else {
                    LOG.error("getDpnIdFromInterfaceNameFromGeniusRPC({}) failed: {}", input, output);
                }
            } else {
                LOG.error("getDpnIdFromInterfaceNameFromGeniusRPC({}) failed (service couldn't be retrieved)", input);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("failed to retrieve target interface name: ", e);
        }
        if (!successful) {
            dpnid = Optional.empty();
        }
        return dpnid;
    }

    /**
     * Given a DPN ID, the method returns its IP addresses.
     *
     * @param theDpnIdType the dataplane id.
     * @return the IP addresses.
     */
    public List<IpAddress> getDpnIpFromGeniusRPC(DpnIdType theDpnIdType) {
        GetEndpointIpForDpnInputBuilder builder = new GetEndpointIpForDpnInputBuilder();
        builder.setDpid(theDpnIdType.getValue());
        GetEndpointIpForDpnInput input = builder.build();
        OdlInterfaceRpcService service = getIfmRpcService();

        if (service == null) {
            LOG.error("Genius RPC service not available {}", input);
            throw new SfcGeniusRuntimeException(new RuntimeException("Genius RPC service not available"));
        }

        try {
            RpcResult<GetEndpointIpForDpnOutput> output = service.getEndpointIpForDpn(input).get();
            if (!output.isSuccessful()) {
                LOG.warn("getDpnIpFromGeniusRPC({}) failed: {}", input, output);
                return Collections.emptyList();
            }
            List<IpAddress> localIps = output.getResult().getLocalIps();
            LOG.trace("getDpnIpFromGeniusRPC({}) succeeded: {}", input, output);
            if (localIps != null) {
                return localIps;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDpnIpFromGeniusRPC failed due to exception", e);
            throw new SfcGeniusRuntimeException(e);
        }

        return Collections.emptyList();
    }

    private ItmRpcService getItmRpcService() {
        return itmRpcService;
    }

    private OdlInterfaceRpcService getIfmRpcService() {
        return ifmRpcService;
    }
}
