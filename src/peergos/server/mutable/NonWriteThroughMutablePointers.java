package peergos.server.mutable;

import peergos.shared.crypto.asymmetric.*;
import peergos.shared.crypto.hash.*;
import peergos.shared.mutable.*;
import peergos.shared.storage.*;

import java.util.*;
import java.util.concurrent.*;

public class NonWriteThroughMutablePointers implements MutablePointers {

    private final MutablePointers source;
    private final ContentAddressedStorage storage;
    private final Map<PublicKeyHash, byte[]> modifications;

    public NonWriteThroughMutablePointers(MutablePointers source, ContentAddressedStorage storage) {
        this.source = source;
        this.storage = storage;
        this.modifications = new HashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> setPointer(PublicKeyHash owner, PublicKeyHash writer, byte[] writerSignedBtreeRootHash) {
        try {
            if (! modifications.containsKey(writer)) {
                Optional<byte[]> existing = source.getPointer(writer).get();
                existing.map(val -> modifications.put(writer, val));
            }
            Optional<PublicSigningKey> opt = storage.getSigningKey(writer).get();
            if (! opt.isPresent())
                throw new IllegalStateException("Couldn't retrieve signing key!");
            boolean validUpdate = MutablePointers.isValidUpdate(opt.get(), Optional.ofNullable(modifications.get(writer)), writerSignedBtreeRootHash);
            if (! validUpdate)
                return CompletableFuture.completedFuture(false);
            modifications.put(writer, writerSignedBtreeRootHash);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getPointer(PublicKeyHash writer) {
        try {
            if (modifications.containsKey(writer))
                return CompletableFuture.completedFuture(Optional.of(modifications.get(writer)));
            return source.getPointer(writer);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
