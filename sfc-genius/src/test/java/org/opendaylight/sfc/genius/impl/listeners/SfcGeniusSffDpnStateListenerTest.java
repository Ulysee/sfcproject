/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sfc.genius.impl.listeners;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.sfc.genius.impl.SfcGeniusServiceManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.dpnid.rsps.dpn.rsps.Dpn;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.dpnid.rsps.dpn.rsps.dpn.RspsForDpnid;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.dpnid.rsps.dpn.rsps.dpn.rsps._for.dpnid.Rsps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;

@RunWith(MockitoJUnitRunner.class)
public class SfcGeniusSffDpnStateListenerTest {

    @Mock
    private DataBroker dataBroker;

    @Mock
    private SfcGeniusServiceManager sfcGeniusServiceManager;

    @Mock
    private ExecutorService executorService;

    @Mock
    private Dpn dpn;

    @Mock
    private RspsForDpnid rspsForDpnid;

    @Mock
    private List<Rsps> rspsList;

    private SfcGeniusSffDpnStateListener sfcGeniusSffDpnStateListener;

    @Before
    public void setup() {
        when(dpn.getDpnId()).thenReturn(new DpnIdType(Uint64.ONE));
        when(dpn.getRspsForDpnid()).thenReturn(rspsForDpnid);
        when(rspsForDpnid.getRsps()).thenReturn(rspsList);
        sfcGeniusSffDpnStateListener = new SfcGeniusSffDpnStateListener(
                dataBroker,
                sfcGeniusServiceManager,
                executorService);
    }

    @Test
    public void remove() throws Exception {
        when(rspsList.isEmpty()).thenReturn(false);
        sfcGeniusSffDpnStateListener.remove(InstanceIdentifier.create(Dpn.class), dpn);
        verify(sfcGeniusServiceManager).unbindNode(Uint64.ONE);
    }

    @Test
    public void removeNoPaths() throws Exception {
        when(rspsList.isEmpty()).thenReturn(true);
        sfcGeniusSffDpnStateListener.remove(InstanceIdentifier.create(Dpn.class), dpn);
        verifyZeroInteractions(sfcGeniusServiceManager);
    }

    @Test
    public void updateAddPaths() throws Exception {
        when(rspsList.isEmpty()).thenReturn(false).thenReturn(true);
        sfcGeniusSffDpnStateListener.update(InstanceIdentifier.create(Dpn.class), dpn, dpn);
        verify(sfcGeniusServiceManager).bindNode(Uint64.ONE);
    }

    @Test
    public void updateAddRemoveSomePaths() throws Exception {
        when(rspsList.isEmpty()).thenReturn(false).thenReturn(false);
        sfcGeniusSffDpnStateListener.update(InstanceIdentifier.create(Dpn.class), dpn, dpn);
        verifyZeroInteractions(sfcGeniusServiceManager);
    }

    @Test
    public void updateRemovePaths() throws Exception {
        when(rspsList.isEmpty()).thenReturn(true).thenReturn(false);
        sfcGeniusSffDpnStateListener.update(InstanceIdentifier.create(Dpn.class), dpn, dpn);
        verify(sfcGeniusServiceManager).unbindNode(Uint64.ONE);
    }

    @Test
    public void add() throws Exception {
        when(rspsList.isEmpty()).thenReturn(false);
        sfcGeniusSffDpnStateListener.add(InstanceIdentifier.create(Dpn.class), dpn);
        verify(sfcGeniusServiceManager).bindNode(Uint64.ONE);
    }

    @Test
    public void addNoPaths() throws Exception {
        when(rspsList.isEmpty()).thenReturn(true);
        sfcGeniusSffDpnStateListener.add(InstanceIdentifier.create(Dpn.class), dpn);
        verifyZeroInteractions(sfcGeniusServiceManager);
    }
}
