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
        assertTrue(customerClass.contains("public virtual IList<Tour> Tours { get; set; } = new ObservableCollection<Tour>();"));
        assertTrue(customerClass.contains("public virtual IList<Reservation> Reservations { get; set; } = new ObservableCollection<Reservation>();"));

        String tourClass = csharpFiles.get("Tour.cs");
        assertNotNull(tourClass);
        assertTrue(tourClass.contains("public virtual string TourName { get; set; }"));
        assertTrue(tourClass.contains("public virtual IList<Customer> Customers { get; set; } = new ObservableCollection<Customer>();"));
        assertTrue(tourClass.contains("public virtual IList<Agreement> Agreements { get; set; } = new ObservableCollection<Agreement>();"));
        assertTrue(tourClass.contains("public virtual IList<Hotel> Hotels { get; set; } = new ObservableCollection<Hotel>();"));
        assertTrue(tourClass.contains("public virtual IList<Room> Rooms { get; set; } = new ObservableCollection<Room>();"));
        assertTrue(tourClass.contains("public virtual IList<Activity> Activities { get; set; } = new ObservableCollection<Activity>();"));
        assertTrue(tourClass.contains("public virtual IList<Payment> Payments { get; set; } = new ObservableCollection<Payment>();"));
        assertTrue(tourClass.contains("public virtual IList<Invoice> Invoices { get; set; } = new ObservableCollection<Invoice>();"));
        assertTrue(tourClass.contains("public virtual TourOperator TourOperator { get; set; }"));
    }

    @Test
    public void testGenerateCSharpFromLabeledPlantUml() {
        String plantUml = "@startuml\n" +
                "class Customer {\n" +
                "  + Name: string\n" +
                "  + Surname: string\n" +
                "  + Email: string\n" +
                "  + Phone: string\n" +
                "  + Nationality: string\n" +
                "  + Birthdate: DateTime\n" +
                "}\n" +
                "class Tour {\n" +
                "  + TourName: string\n" +
                "  + Date: DateTime\n" +
                "  + Duration: int\n" +
                "}\n" +
                "class Agreement {\n" +
                "  - AgreementDate: DateTime\n" +
                "  - Terms: string\n" +
                "  - Confirmed: bool\n" +
                "}\n" +
                "CustomerTour \"CustomerTour\" <--> \"Agreement\" Agreement\n" +
                "CustomerTour \"* CustomerTours\" <--> \"CustomerAtTour\" Customer\n" +
                "CustomerTour \"* CustomerTours\" <--> \"Tour\" Tour\n" +
                "class CustomerTour {\n" +
                "  + Name: string\n" +
                "}\n" +
                "@enduml";

        Map<String, String> files = service.generate(plantUml);

        String customerClass = files.get("Customer.cs");
        assertNotNull(customerClass);
        assertTrue(customerClass.contains("public virtual IList<CustomerTour> CustomerTours { get; set; } = new ObservableCollection<CustomerTour>();"));

        String customerTourClass = files.get("CustomerTour.cs");
        assertNotNull(customerTourClass);
        assertTrue(customerTourClass.contains("public virtual Customer CustomerAtTour { get; set; }"));
        assertTrue(customerTourClass.contains("public virtual Tour Tour { get; set; }"));

        String tourClass = files.get("Tour.cs");
        assertNotNull(tourClass);
        assertTrue(tourClass.contains("public virtual IList<CustomerTour> CustomerTours { get; set; } = new ObservableCollection<CustomerTour>();"));

        String agreementClass = files.get("Agreement.cs");
        assertNotNull(agreementClass);
        assertTrue(agreementClass.contains("public virtual CustomerTour CustomerTour { get; set; }"));
    }
}
