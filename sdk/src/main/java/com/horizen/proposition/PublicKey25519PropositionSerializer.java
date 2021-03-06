package com.horizen.proposition;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class PublicKey25519PropositionSerializer implements PropositionSerializer<PublicKey25519Proposition> {
    private static PublicKey25519PropositionSerializer serializer;

    static {
        serializer = new PublicKey25519PropositionSerializer();
    }

    private PublicKey25519PropositionSerializer() {
        super();
    }

    public static PublicKey25519PropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(PublicKey25519Proposition proposition, Writer writer) {
        writer.putBytes(proposition.bytes());
    }

    @Override
    public PublicKey25519Proposition parse(Reader reader) {
        return PublicKey25519Proposition.parseBytes(reader.getBytes(reader.remaining()));
    }
}
