/*
 * Copyright Bosch.IO GmbH 2020.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.attachment;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.eclipse.sw360.datahandler.thrift.Source;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentService;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentUsage;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.eclipse.sw360.rest.resourceserver.core.ThriftServiceProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class Sw360AttachmentServiceTest {

    @Value("${sw360.thrift-server-url:http://localhost:8080}")
    private String thriftServerUrl;

    @Spy
    private ThriftServiceProvider<AttachmentService.Iface> serviceProvider;

    @Mock
    private RestControllerHelper<?> restControllerHelper;

    @Mock
    private AttachmentService.Iface thriftService;

    @InjectMocks
    private Sw360AttachmentService attachmentService;

    private int sourceIdCounter;

    @Before
    public void setUp() throws TTransportException {
        when(serviceProvider.getService(thriftServerUrl)).thenReturn(thriftService);
    }

    private static String attachmentId(int idx) {
        return "at" + idx;
    }

    private static Attachment createAttachment(int idx) {
        return new Attachment(attachmentId(idx), "file" + idx);
    }

    private static Set<Attachment> createAttachmentSet(int count) {
        return IntStream.range(1, count + 1)
                .mapToObj(Sw360AttachmentServiceTest::createAttachment)
                .collect(Collectors.toSet());
    }

    private Source createSource(Function<String, Source> srcCreator) {
        return srcCreator.apply("id" + (++sourceIdCounter));
    }

    private Source createSource() {
        return createSource(Source::releaseId);
    }

    private AttachmentUsage createAttachmentUsage(int idx, Function<String, Source> ownerSrcCreator) {
        return new AttachmentUsage(createSource(), attachmentId(idx), createSource(ownerSrcCreator));
    }

    private AttachmentUsage createAttachmentUsage(int idx) {
        return createAttachmentUsage(idx, Source::releaseId);
    }

    @Test
    public void testFilterAttachmentsToRemoveIgnoresUnknownAttachments() throws TException {
        Set<Attachment> attachmentSet = createAttachmentSet(4);

        Set<Attachment> filtered = attachmentService.filterAttachmentsToRemove(Source.releaseId("r"),
                new HashSet<>(attachmentSet), Arrays.asList(attachmentId(10), attachmentId(11)));
        assertThat(filtered).isEmpty();
        verifyZeroInteractions(thriftService);
    }

    @Test
    public void testFilterAttachmentsToRemoveAllValid() throws TException {
        Source owner = createSource();
        Set<Attachment> allAttachments = createAttachmentSet(3);
        AttachmentUsage usage1 = createAttachmentUsage(2);
        AttachmentUsage usage2 = createAttachmentUsage(2);
        AttachmentUsage usage3 = createAttachmentUsage(3);
        when(thriftService.getAttachmentUsages(owner, attachmentId(2), null))
                .thenReturn(Arrays.asList(usage1, usage2));
        when(thriftService.getAttachmentUsages(owner, attachmentId(3), null))
                .thenReturn(Collections.singletonList(usage3));

        Set<Attachment> filtered = attachmentService.filterAttachmentsToRemove(owner, allAttachments,
                Arrays.asList(attachmentId(2), attachmentId(3)));
        assertThat(filtered).containsOnly(createAttachment(2), createAttachment(3));
    }

    @Test
    public void testFilterAttachmentsToRemoveEvaluatesAttachmentUsages() throws TException {
        Source owner = createSource();
        List<AttachmentUsage> usages1 = Arrays.asList(createAttachmentUsage(1),
                createAttachmentUsage(1, Source::projectId));
        List<AttachmentUsage> usages2 = Arrays.asList(createAttachmentUsage(2), createAttachmentUsage(2));
        Set<Attachment> allAttachments = createAttachmentSet(2);
        when(thriftService.getAttachmentUsages(owner, attachmentId(1), null))
                .thenReturn(usages1);
        when(thriftService.getAttachmentUsages(owner, attachmentId(2), null))
                .thenReturn(usages2);

        Set<Attachment> filtered = attachmentService.filterAttachmentsToRemove(owner, allAttachments,
                Arrays.asList(attachmentId(1), attachmentId(2)));
        assertThat(filtered).containsOnly(createAttachment(2));
    }

    @Test
    public void testFilterAttachmentsToRemoveEvaluatesCheckStatus() throws TException {
        Source owner = createSource();
        Attachment attachment1 = createAttachment(1);
        Attachment attachment2 = createAttachment(2);
        attachment2.setCheckStatus(CheckStatus.ACCEPTED);
        Set<Attachment> attachments = new HashSet<>(Arrays.asList(attachment1, attachment2));
        when(thriftService.getAttachmentUsages(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        Set<Attachment> filtered = attachmentService.filterAttachmentsToRemove(owner, attachments,
                Arrays.asList(attachmentId(1), attachmentId(2)));
        assertThat(filtered).containsOnly(attachment1);
    }

    @Test
    public void testFilterAttachmentsToRemoveHandlesExceptions() throws TException {
        Source owner = createSource();
        when(thriftService.getAttachmentUsages(owner, attachmentId(1), null))
                .thenThrow(new TException("Thrift failure"));
        when(thriftService.getAttachmentUsages(owner, attachmentId(2), null))
                .thenReturn(Collections.emptyList());
        Set<Attachment> allAttachments = createAttachmentSet(3);

        Set<Attachment> filtered = attachmentService.filterAttachmentsToRemove(owner, allAttachments,
                Arrays.asList(attachmentId(1), attachmentId(2)));
        assertThat(filtered).containsOnly(createAttachment(2));
        verify(thriftService).getAttachmentUsages(owner, attachmentId(2), null);
    }
}
