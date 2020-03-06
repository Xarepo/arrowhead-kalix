package eu.arrowhead.kalix.dto;

import eu.arrowhead.kalix.dto.types.DtoDescriptor;
import eu.arrowhead.kalix.dto.types.DtoType;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Map;
import java.util.Objects;

public class DtoProperty implements DtoType {
    private final ExecutableElement parentElement;
    private final String name;
    private final Map<DataEncoding, String> encodingNames;
    private final DtoType type;
    private final boolean isOptional;

    private DtoProperty(final Builder builder) {
        parentElement = Objects.requireNonNull(builder.parentElement, "Expected parentElement");
        name = Objects.requireNonNull(builder.name, "Expected name");
        encodingNames = Objects.requireNonNull(builder.encodingNames, "Expected encodingNames");
        type = Objects.requireNonNull(builder.type, "Expected type");
        isOptional = builder.isOptional;
    }

    public ExecutableElement parentElement() {
        return parentElement;
    }

    public String name() {
        return name;
    }

    public String nameFor(final DataEncoding dataEncoding) {
        return encodingNames.getOrDefault(dataEncoding, name);
    }

    public DtoType type() {
        return type;
    }

    @Override
    public DtoDescriptor descriptor() {
        return type.descriptor();
    }

    @Override
    public TypeMirror asTypeMirror() {
        return type.asTypeMirror();
    }

    public boolean isOptional() {
        return isOptional;
    }

    public static class Builder {
        private ExecutableElement parentElement;
        private String name;
        private Map<DataEncoding, String> encodingNames;
        private DtoType type;
        private boolean isOptional;

        public Builder parentElement(final ExecutableElement parentElement) {
            this.parentElement = parentElement;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder encodingNames(final Map<DataEncoding, String> encodingNames) {
            this.encodingNames = encodingNames;
            return this;
        }

        public Builder type(final DtoType type) {
            this.type = type;
            return this;
        }

        public Builder isOptional(final boolean isOptional) {
            this.isOptional = isOptional;
            return this;
        }

        public DtoProperty build() {
            return new DtoProperty(this);
        }
    }
}
