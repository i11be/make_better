package com.makemebetter.service;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryImage;
import com.makemebetter.domain.EntryKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 动态流：把动态和它的配图拼在一起给页面用。 */
@Service
@Transactional(readOnly = true)
public class FeedService {

    /** 一张图在页面上的两个地址（原图 + 九宫格缩略图） */
    public record FeedImage(long id, String url, String thumbUrl) {
    }

    public record Post(Entry entry, List<FeedImage> images) {
    }

    private final EntryService entries;

    public FeedService(EntryService entries) {
        this.entries = entries;
    }

    public List<Post> all() {
        List<Post> posts = new ArrayList<>();
        for (Entry entry : entries.list(EntryKind.FEED)) {
            posts.add(new Post(entry, imagesOf(entry.getId())));
        }
        return posts;
    }

    public List<Post> latest(int limit) {
        List<Post> posts = new ArrayList<>();
        for (Entry entry : entries.latest(EntryKind.FEED, limit)) {
            posts.add(new Post(entry, imagesOf(entry.getId())));
        }
        return posts;
    }

    public List<FeedImage> imagesOf(Long entryId) {
        List<FeedImage> images = new ArrayList<>();
        for (EntryImage image : entries.imagesOf(entryId)) {
            images.add(new FeedImage(image.getId(),
                    "/files/" + image.getPath(),
                    "/files/" + FileStorage.thumbOf(image.getPath())));
        }
        return images;
    }
}
