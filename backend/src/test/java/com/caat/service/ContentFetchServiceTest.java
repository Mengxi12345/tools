package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Content;
import com.caat.entity.FetchTask;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.repository.ContentRepository;
import com.caat.repository.FetchTaskRepository;
import com.caat.repository.TrackedUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 针对 ContentFetchService 核心保存流程的单元测试（不依赖真实数据库与外部平台）。
 */
class ContentFetchServiceTest {

    private TrackedUserRepository trackedUserRepository;
    private ContentRepository contentRepository;
    private FetchTaskRepository fetchTaskRepository;
    private AdapterFactory adapterFactory;
    private ObjectMapper objectMapper;
    private ElasticsearchService elasticsearchService;
    private NotificationService notificationService;
    private ContentAssetService contentAssetService;
    private ZsxqFileService zsxqFileService;

    private ContentFetchService service;

    @BeforeEach
    void setUp() {
        trackedUserRepository = mock(TrackedUserRepository.class);
        contentRepository = mock(ContentRepository.class);
        fetchTaskRepository = mock(FetchTaskRepository.class);
        adapterFactory = mock(AdapterFactory.class);
        objectMapper = new ObjectMapper();
        elasticsearchService = mock(ElasticsearchService.class);
        notificationService = mock(NotificationService.class);
        contentAssetService = mock(ContentAssetService.class);
        zsxqFileService = mock(ZsxqFileService.class);

        service = new ContentFetchService(
            trackedUserRepository,
            contentRepository,
            fetchTaskRepository,
            adapterFactory,
            objectMapper,
            elasticsearchService,
            notificationService,
            contentAssetService,
            zsxqFileService
        );
    }

    @Test
    void saveContent_shouldPersistAndNotify_whenContentNotExists() {
        Platform platform = new Platform();
        platform.setType("OTHER");
        TrackedUser user = new TrackedUser();
        user.setId(UUID.randomUUID());

        PlatformContent pc = new PlatformContent();
        pc.setContentId("cid-1");
        pc.setTitle("title");
        pc.setBody("body");
        pc.setUrl("http://example.com");
        pc.setContentType(PlatformContent.ContentType.TEXT);
        pc.setPublishedAt(LocalDateTime.now());
        pc.setMediaUrls(Collections.emptyList());

        when(contentRepository.existsByHash(any())).thenReturn(false);
        when(contentRepository.save(any(Content.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Content saved = service.saveContent(pc, platform, user);

        assertThat(saved).isNotNull();
        assertThat(saved.getContentId()).isEqualTo("cid-1");
        verify(contentRepository).save(any(Content.class));
        verify(notificationService).checkAndNotify(any(Content.class));
    }

    @Test
    void saveContent_shouldUseTimestoreAssetProcessor_whenPlatformIsTimeStore() {
        Platform platform = new Platform();
        platform.setType("TIMESTORE");
        TrackedUser user = new TrackedUser();
        user.setId(UUID.randomUUID());

        PlatformContent pc = new PlatformContent();
        pc.setContentId("cid-2");
        pc.setTitle("title");
        pc.setBody("<p><img src=\"https://img.example.com/a.png\" /></p>");
        pc.setUrl("http://example.com");
        pc.setContentType(PlatformContent.ContentType.TEXT);
        pc.setPublishedAt(LocalDateTime.now());
        pc.setMediaUrls(Collections.singletonList("https://img.example.com/a.png"));

        when(contentRepository.existsByHash(any())).thenReturn(false);
        when(contentRepository.save(any(Content.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentAssetService.downloadImageAndSave(any())).thenReturn("/api/v1/uploads/a.png");

        Content saved = service.saveContent(pc, platform, user);

        assertThat(saved).isNotNull();
        assertThat(saved.getMediaUrls()).isNotEmpty();
        verify(contentAssetService).downloadImageAndSave(any());
    }
}

