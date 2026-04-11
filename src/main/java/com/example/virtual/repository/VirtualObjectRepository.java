package com.example.virtual.repository;

import com.example.virtual.domain.VirtualObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class VirtualObjectRepository {
    private static final Logger log = LoggerFactory.getLogger(VirtualObjectRepository.class);
    private static final Path STORE_PATH = Path.of("data", "virtual-objects.ser");

    private final Map<Long, VirtualObject> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public VirtualObjectRepository() {
        loadFromDisk();
    }

    public synchronized List<VirtualObject> findByParentId(Long parentId) {
        List<VirtualObject> result = new ArrayList<>();
        for (VirtualObject object : store.values()) {
            if (parentId == null) {
                if (object.getParentId() == null) {
                    result.add(object);
                }
            } else if (parentId.equals(object.getParentId())) {
                result.add(object);
            }
        }
        return result;
    }

    public synchronized List<VirtualObject> findByName(String name) {
        List<VirtualObject> result = new ArrayList<>();
        String target = name.toLowerCase();
        for (VirtualObject object : store.values()) {
            if (object.getName() != null && object.getName().toLowerCase().contains(target)) {
                result.add(object);
            }
        }
        return result;
    }

    public synchronized VirtualObject save(VirtualObject object) {
        if (object.getId() == null) {
            object.setId(idGenerator.incrementAndGet());
            if (object.getCreateTime() == null) {
                object.setCreateTime(LocalDateTime.now());
            }
        }
        object.setUpdateTime(LocalDateTime.now());
        store.put(object.getId(), object);
        persistToDisk();
        return object;
    }

    public synchronized Optional<VirtualObject> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public synchronized void deleteById(Long id) {
        store.remove(id);
        persistToDisk();
    }

    private synchronized void loadFromDisk() {
        if (!Files.exists(STORE_PATH)) {
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(STORE_PATH))) {
            StoreSnapshot snapshot = (StoreSnapshot) in.readObject();
            store.clear();
            for (VirtualObject object : snapshot.objects()) {
                store.put(object.getId(), object);
            }
            idGenerator.set(snapshot.lastId());
            log.info("Loaded {} virtual objects from {}", store.size(), STORE_PATH.toAbsolutePath());
        } catch (Exception ex) {
            log.warn("Failed to load virtual object store from {}: {}", STORE_PATH.toAbsolutePath(), ex.getMessage());
        }
    }

    private synchronized void persistToDisk() {
        try {
            if (STORE_PATH.getParent() != null) {
                Files.createDirectories(STORE_PATH.getParent());
            }
            StoreSnapshot snapshot = new StoreSnapshot(idGenerator.get(), new ArrayList<>(store.values()));
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(STORE_PATH))) {
                out.writeObject(snapshot);
            }
        } catch (IOException ex) {
            log.error("Failed to persist virtual object store to {}: {}", STORE_PATH.toAbsolutePath(), ex.getMessage());
        }
    }

    private record StoreSnapshot(long lastId, List<VirtualObject> objects) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
