package se.arkalix.dto;

import com.squareup.javapoet.TypeSpec;

// TODO: Make it possible to use custom DtoImplementer implementations.
public interface DtoImplementer {
    DtoCodecSpec codec();

    void implementFor(DtoTarget target, TypeSpec.Builder implementation);
}
