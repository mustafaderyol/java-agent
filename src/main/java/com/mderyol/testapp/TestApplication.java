package com.mderyol.testapp;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TestApplication {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================================");
        System.out.println("   Starting E-Commerce Simulation Test Application (15+ Methods)  ");
        System.out.println("==================================================================");
        System.out.println("Simulating continuous orders every 3 seconds...");
        System.out.println("You can now send Dynamic Probe requests to: http://localhost:9876");
        System.out.println("Press Ctrl+C to terminate.\n");

        OrderController controller = new OrderController();

        int counter = 1;
        while (true) {
            String orderId = "ORD-" + (1000 + counter);
            String customerId = "CUST-" + (100 + (counter % 5));
            double amount = 50.0 + ThreadLocalRandom.current().nextDouble(450.0);

            System.out.println("\n>>> [MAIN APP] Triggering Order #" + counter + " (" + orderId + ", Amount: $" + String.format("%.2f", amount) + ") <<<");
            try {
                Map<String, Object> result = controller.checkoutOrder(orderId, customerId, amount);
                System.out.println("<<< [MAIN APP] Order #" + counter + " Completed: " + result.get("status") + " <<<");
            } catch (Exception e) {
                System.err.println("<<< [MAIN APP] Order #" + counter + " Failed: " + e.getMessage() + " <<<");
            }

            counter++;
            Thread.sleep(3000);
        }
    }

    // 1. Controller Seviyesi
    public static class OrderController {
        private final OrderService orderService = new OrderService();
        private final PaymentService paymentService = new PaymentService();
        private final NotificationService notificationService = new NotificationService();
        private final ShippingService shippingService = new ShippingService();

        public Map<String, Object> checkoutOrder(String orderId, String customerId, double amount) {
            // 2. Metot: Sipariş Doğrulama
            orderService.validateOrder(orderId, customerId, amount);

            // 7. Metot: Ödeme İşleme
            Map<String, Object> paymentResult = paymentService.processPayment(orderId, customerId, amount, "CARD-TOKEN-XYZ-9988");

            // 11. Metot: Bildirim Gönderimi
            notificationService.sendOrderConfirmation(orderId, customerId);

            // 14. Metot: Kargo Etiketi Oluşturma
            String trackingNumber = shippingService.createShipment(orderId, customerId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId", orderId);
            response.put("status", "SUCCESS");
            response.put("payment", paymentResult);
            response.put("trackingNumber", trackingNumber);
            return response;
        }
    }

    // 2. Order Servisi
    public static class OrderService {
        private final CustomerKycService kycService = new CustomerKycService();
        private final InventoryService inventoryService = new InventoryService();

        public void validateOrder(String orderId, String customerId, double amount) {
            // 3. Metot: Müşteri KYC ve Risk Kontrolü
            kycService.checkCustomerKyc(customerId);

            // 6. Metot: Stok Rezervasyonu
            inventoryService.reserveStock(orderId, "ITEM-" + orderId.hashCode());
        }
    }

    // 3. Müşteri & Risk Servisi
    public static class CustomerKycService {
        private final FraudDetectionService fraudService = new FraudDetectionService();

        public boolean checkCustomerKyc(String customerId) {
            simulateLatency(15);
            // 4. Metot: Fraud Risk Skoru Hesaplama
            int riskScore = fraudService.calculateRiskScore(customerId);
            return riskScore < 80;
        }
    }

    // 4. Fraud Detection
    public static class FraudDetectionService {
        private final BlacklistService blacklistService = new BlacklistService();

        public int calculateRiskScore(String customerId) {
            simulateLatency(10);
            // 5. Metot: Kara Liste Kontrolü
            boolean blacklisted = blacklistService.isBlacklisted(customerId);
            return blacklisted ? 99 : 12;
        }
    }

    // 5. Blacklist Servisi
    public static class BlacklistService {
        public boolean isBlacklisted(String customerId) {
            simulateLatency(5);
            return customerId.endsWith("9");
        }
    }

    // 6. Envanter & Depo Servisi
    public static class InventoryService {
        private final WarehouseService warehouseService = new WarehouseService();

        public boolean reserveStock(String orderId, String itemSku) {
            simulateLatency(20);
            // 7. Metot: Depo Raf Konumu Bulma
            String shelfLocation = warehouseService.locateShelf(itemSku);
            return shelfLocation != null;
        }
    }

    // 7. Depo Servisi
    public static class WarehouseService {
        public String locateShelf(String itemSku) {
            simulateLatency(10);
            return "AISLE-4-SHELF-B";
        }
    }

    // 8. Ödeme Servisi (Uzaktan Probe için Mükemmel Aday!)
    public static class PaymentService {
        private final BankGatewayService bankGateway = new BankGatewayService();
        private final CurrencyConverterService currencyConverter = new CurrencyConverterService();
        private final AccountingService accountingService = new AccountingService();

        public Map<String, Object> processPayment(String orderId, String customerId, double amount, String cardToken) {
            simulateLatency(35);

            // 9. Metot: Para Birimi Dönüşümü
            double usdAmount = currencyConverter.convertToUsd(amount, "EUR");

            // 10. Metot: Banka Kart Provizyonu
            String authCode = bankGateway.authorizeCreditCard(customerId, usdAmount, cardToken);

            // 11. Metot: Muhasebe Defter Kaydı
            accountingService.createLedgerEntry(orderId, usdAmount, authCode);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("paymentId", "PAY-" + UUID.randomUUID().toString().substring(0, 8));
            result.put("amountUsd", usdAmount);
            result.put("authCode", authCode);
            result.put("status", "APPROVED");
            return result;
        }
    }

    // 9. Para Birimi Servisi
    public static class CurrencyConverterService {
        public double convertToUsd(double amount, String fromCurrency) {
            simulateLatency(8);
            return amount * 1.08;
        }
    }

    // 10. Banka Entegrasyon Servisi
    public static class BankGatewayService {
        public String authorizeCreditCard(String customerId, double amount, String cardToken) {
            simulateLatency(50);
            if ("CUST-104".equals(customerId) || amount > 460.0) {
                throw new IllegalStateException("Bank authorization declined for customer " + customerId
                        + ": Daily credit limit exceeded (Requested: $" + String.format(Locale.US, "%.2f", amount) + ")",
                        new java.net.SocketTimeoutException("Read timed out from 3D-Secure payment processor"));
            }
            return "AUTH-OK-" + ThreadLocalRandom.current().nextInt(100000, 999999);
        }
    }

    // 11. Muhasebe Servisi
    public static class AccountingService {
        public void createLedgerEntry(String orderId, double amount, String authCode) {
            simulateLatency(12);
        }
    }

    // 12. Bildirim Servisi
    public static class NotificationService {
        private final EmailService emailService = new EmailService();
        private final SmsService smsService = new SmsService();

        public void sendOrderConfirmation(String orderId, String customerId) {
            simulateLatency(10);
            // 13. Metot: E-Posta Gönderimi
            emailService.sendOrderEmail(customerId, "Your order " + orderId + " has been confirmed!");

            // 14. Metot: SMS Gönderimi
            smsService.sendOrderSms(customerId, "Order " + orderId + " approved.");
        }
    }

    // 13. E-Posta Servisi
    public static class EmailService {
        public boolean sendOrderEmail(String customerId, String body) {
            simulateLatency(25);
            return true;
        }
    }

    // 14. SMS Servisi
    public static class SmsService {
        public boolean sendOrderSms(String customerId, String text) {
            simulateLatency(15);
            return true;
        }
    }

    // 15. Kargo Servisi
    public static class ShippingService {
        private final CarrierIntegrationService carrierService = new CarrierIntegrationService();

        public String createShipment(String orderId, String customerId) {
            simulateLatency(20);
            // 16. Metot: Kargo Takip Kodu Üretimi
            return carrierService.generateTrackingCode("DHL_EXPRESS", orderId);
        }
    }

    // 16. Kargo Entegratörü
    public static class CarrierIntegrationService {
        public String generateTrackingCode(String carrierName, String orderId) {
            simulateLatency(30);
            return carrierName + "-TRK-" + ThreadLocalRandom.current().nextLong(100000000L, 999999999L);
        }
    }

    private static void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {}
    }
}
