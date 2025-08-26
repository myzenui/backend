import java.util.Map;

public class DebugTest {
    public static void main(String[] args) {
        PlantUmlToCSharpService service = new PlantUmlToCSharpService();
        
        String plantUml = "@startuml\n" +
                "class Customer {\n" +
                "  + Name: string\n" +
                "}\n" +
                "class CustomerTour {\n" +
                "  + Name: string\n" +
                "}\n" +
                "CustomerTour \"* CustomerTours\" <--> \"CustomerAtTour\" Customer\n" +
                "@enduml";
        
        Map<String, String> files = service.generate(plantUml);
        
        System.out.println("Generated files:");
        for (String filename : files.keySet()) {
            System.out.println("=== " + filename + " ===");
            System.out.println(files.get(filename));
            System.out.println();
        }
    }
}
