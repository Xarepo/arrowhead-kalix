package eu.arrowhead.kalix.dto.types;

import eu.arrowhead.kalix.dto.Format;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DTOInterface implements DTOType {
    private final DeclaredType interfaceType;
    private final Set<Format> readableFormats;
    private final Set<Format> writableFormats;
    private final String simpleName;
    private final String simpleNameDTO;
    private final Set<Format> formats;

    public DTOInterface(
        final DeclaredType interfaceType,
        final Format[] readableFormats,
        final Format[] writableFormats
    ) {
        this.interfaceType = interfaceType;
        this.readableFormats = Stream.of(readableFormats).collect(Collectors.toSet());
        this.writableFormats = Stream.of(writableFormats).collect(Collectors.toSet());

        final TypeElement interfaceElement = (TypeElement) interfaceType.asElement();
        simpleName = interfaceElement.getSimpleName().toString();
        simpleNameDTO = simpleName + "DTO";
        formats = new HashSet<>();
        formats.addAll(this.readableFormats);
        formats.addAll(this.writableFormats);
    }

    public String simpleName() {
        return simpleName;
    }

    public String simpleNameDTO() {
        return simpleNameDTO;
    }

    public Set<Format> formats() {
        return formats;
    }

    @Override
    public DTODescriptor descriptor() {
        return DTODescriptor.INTERFACE;
    }

    @Override
    public DeclaredType asTypeMirror() {
        return interfaceType;
    }

    public boolean isReadable(final Format format) {
        return readableFormats.contains(format);
    }

    public boolean isWritable(final Format format) {
        return writableFormats.contains(format);
    }

    @Override
    public String toString() {
        return simpleName;
    }
}
