package visitors;


import java.lang.reflect.Parameter;

public class VisitorUtils {

    private final static String REGEX = "^(java\\.|javax\\.*|org\\.ietf\\.|org\\.omg\\.|org\\.w3c\\.|org.xml.).*$";

    /**
     * Private Constructor will prevent the instantiation of this class directly
     */
    private VisitorUtils() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    public static boolean isStandardJavaLibrary(String fullClassName) {
        // Check for primitive types
        if (isPrimitiveType(fullClassName)) {
            return true;
        }

        if (fullClassName.matches(REGEX)) {
            return true;
        }

        return false;
    }
    
    /**
     * Checks if the provided type name is a Java primitive type
     * 
     * @param typeName the name of the type to check
     * @return true if the type is a primitive type or null/empty, false otherwise
     */
    public static boolean isPrimitiveType(String typeName) {
        return typeName == null || typeName.isEmpty() || 
               typeName.equals("int") || typeName.equals("boolean") || 
               typeName.equals("char") || typeName.equals("byte") || 
               typeName.equals("short") || typeName.equals("long") || 
               typeName.equals("float") || typeName.equals("double") || 
               typeName.equals("void");
    }

    public boolean isUserDefinedClass(Parameter parameter) {


        if (isStandardJavaLibrary(parameter.getType().getPackageName())) {
            return false;
        }

        return true;
    }
}
