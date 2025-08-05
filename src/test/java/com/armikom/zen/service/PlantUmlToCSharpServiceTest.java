package com.armikom.zen.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlantUmlToCSharpServiceTest {

    private final PlantUmlToCSharpService service = new PlantUmlToCSharpService();

    @Test
    public void testGenerateCSharpFromPlantUml() {
        String plantUml = "@startuml\n" +
                "class Customer {\n" +
                "  + name: String\n" +
                "  + email: String\n" +
                "  + phone: String\n" +
                "  + dateOfBirth: Date\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class Tour {\n" +
                "  + tourName: String\n" +
                "  + duration: int\n" +
                "  + itinerary: List<Activity>\n" +
                "  + id: int\n" +
                "  + photoAlbumLink: String\n" +
                "  + description: String\n" +
                "  + startDate: Date\n" +
                "  + endDate: Date\n" +
                "  + transportDescription: String\n" +
                "}\n" +
                "\n" +
                "class Activity {\n" +
                "  + startDate: Date\n" +
                "  + endDate: Date\n" +
                "  + location: String\n" +
                "  + details: String\n" +
                "  + measures: String\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class Hotel {\n" +
                "  + hotelName: String\n" +
                "  + address: String\n" +
                "  + rating: float\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class Room {\n" +
                "  + roomNumber: int\n" +
                "  + capacity: int\n" +
                "  + smokingAllowed: boolean\n" +
                "  + floor: int\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class Agreement {\n" +
                "  + agreementDate: Date\n" +
                "  + terms: String\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class TourOperator {\n" +
                "  + operatorName: String\n" +
                "  + phone: String\n" +
                "  + email: String\n" +
                "  + address: String\n" +
                "  + website: String\n" +
                "  + services: List<String>\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class Reservation {\n" +
                "  + customerId: int\n" +
                "  + roomId: int\n" +
                "  + checkInDate: Date\n" +
                "  + checkOutDate: Date\n" +
                "}\n" +
                "\n" +
                "class Payment {\n" +
                "  + amount: float\n" +
                "  + paymentDate: Date\n" +
                "  + tourId: int\n" +
                "  + customerId: int\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "class Invoice {\n" +
                "  + invoiceNumber: String\n" +
                "  + tourId: int\n" +
                "  + totalAmount: float\n" +
                "  + issueDate: Date\n" +
                "  + id: int\n" +
                "}\n" +
                "\n" +
                "Customer -- Tour : makes reservation >\n" +
                "Tour *-- Agreement : includes >\n" +
                "Tour o-- Hotel : stays at >\n" +
                "Hotel -- Agreement : includes >\n" +
                "Hotel o-- Room : has >\n" +
                "Tour o-- Room : is reserved >\n" +
                "TourOperator -- Tour : offers >\n" +
                "Tour -- Activity : includes >\n" +
                "Room o-- Reservation : is booked >\n" +
                "Customer o-- Reservation : makes >\n" +
                "Tour o-- Customer : includes >\n" +
                "Payment -- Tour : related to >\n" +
                "Invoice -- Tour : related to >\n" +
                "@enduml";

        Map<String, String> csharpFiles = service.generate(plantUml);

        assertNotNull(csharpFiles);
        assertEquals(10, csharpFiles.size());

        String customerClass = csharpFiles.get("Customer.cs");
        assertNotNull(customerClass);
        assertTrue(customerClass.contains("public virtual string Name { get; set; }"));
        assertTrue(customerClass.contains("public virtual ICollection<Tour> Tours { get; set; } = new HashSet<Tour>();"));
        assertTrue(customerClass.contains("public virtual ICollection<Reservation> Reservations { get; set; } = new HashSet<Reservation>();"));

        String tourClass = csharpFiles.get("Tour.cs");
        assertNotNull(tourClass);
        assertTrue(tourClass.contains("public virtual string TourName { get; set; }"));
        assertTrue(tourClass.contains("public virtual ICollection<Customer> Customers { get; set; } = new HashSet<Customer>();"));
        assertTrue(tourClass.contains("public virtual ICollection<Agreement> Agreements { get; set; } = new HashSet<Agreement>();"));
        assertTrue(tourClass.contains("public virtual ICollection<Hotel> Hotels { get; set; } = new HashSet<Hotel>();"));
        assertTrue(tourClass.contains("public virtual ICollection<Room> Rooms { get; set; } = new HashSet<Room>();"));
        assertTrue(tourClass.contains("public virtual ICollection<Activity> Activities { get; set; } = new HashSet<Activity>();"));
        assertTrue(tourClass.contains("public virtual ICollection<Payment> Payments { get; set; } = new HashSet<Payment>();"));
        assertTrue(tourClass.contains("public virtual ICollection<Invoice> Invoices { get; set; } = new HashSet<Invoice>();"));
        assertTrue(tourClass.contains("public virtual TourOperator TourOperator { get; set; }"));
    }
}
