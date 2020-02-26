package eu.arrowhead.kalix.dto.types;

import eu.arrowhead.kalix.dto.Format;

public interface DTOPrimitive extends DTOType {
    DTOPrimitiveType primitiveType();

    @Override
    default boolean isCollection() {
        return false;
    }

    @Override
    default boolean isReadable(final Format format) {
        return format != Format.JSON || primitiveType() != DTOPrimitiveType.CHARACTER;
    }

    @Override
    default boolean isWritable(final Format format) {
        return format != Format.JSON || primitiveType() != DTOPrimitiveType.CHARACTER;
    }
}
