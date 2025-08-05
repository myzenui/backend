package com.armikom.zen.service;

public class Common {
    public static String model = "@startuml\n" +
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
}
