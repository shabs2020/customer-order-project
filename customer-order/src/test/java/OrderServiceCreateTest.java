import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customer.order.service.client.CatalogClient;
import com.customer.order.service.database.embeddables.Customer;
import com.customer.order.service.database.embeddables.PaymentMethod;
import com.customer.order.service.database.embeddables.Site;
import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.entities.IdempotencyKey;
import com.customer.order.service.database.entities.OrderItems;
import com.customer.order.service.database.enums.OrderCategory;
import com.customer.order.service.database.enums.OrderState;
import com.customer.order.service.database.enums.PaymentType;
import com.customer.order.service.database.repositories.CustomerOrderRepository;
import com.customer.order.service.database.repositories.IdempotencyRepository;
import com.customer.order.service.dto.CustomerDTO;
import com.customer.order.service.dto.OrderCreateDTO;
import com.customer.order.service.dto.OrderItemDTO;
import com.customer.order.service.dto.PaymentTypeDTO;
import com.customer.order.service.dto.SiteDTO;
import com.customer.order.service.mappers.EntityToDtoMapping;
import com.customer.order.service.service.OrderService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
public class OrderServiceCreateTest {
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private CatalogClient catalogClient;

    private EntityToDtoMapping mapper;

    private OrderService orderService;

    private UUID orderId;
    private CustomerOrder newOrderEntity;
    private OrderCreateDTO createDTO;
    private final String KEY = "unique-req-123";

    @BeforeEach
    void setUp() {
        this.mapper = new EntityToDtoMapping();
        this.orderService = new OrderService(this.orderRepository,
                this.idempotencyRepository,this.catalogClient,
                this.mapper);

        orderId = UUID.randomUUID();
        newOrderEntity = CustomerOrder.builder()
                .id(orderId)
                .category(OrderCategory.B2B)
                .customer(Customer.builder().id("CUST-001").build())
                .site(Site.builder().id("SITE-001").build())
                .paymentMethod(PaymentMethod.builder().paymentType(PaymentType.INVOICE).build())
                .state(OrderState.DRAFT)
                .orderItems(List.of(OrderItems.builder().id(1L).productOfferingId("po-1").quantity(1).build()))
                .build();
        createDTO= new OrderCreateDTO(
                "B2B",new CustomerDTO("CUST-01"), new SiteDTO("SITE-A"),
                List.of(new OrderItemDTO("PROD-1", 1)),
                new PaymentTypeDTO(PaymentType.INVOICE.name(), null)
        );
    }

    @Test
    @DisplayName("Should create order normally when key is null")
    void createOrder_NoKey_Success() {

        when(orderRepository.save(any())).thenReturn(newOrderEntity);

        var result = orderService.createOrder(createDTO, null);

        // Assert
        assertFalse(result.isReplay());
        verify(idempotencyRepository, never()).saveAndFlush(any());
        verify(catalogClient).verifyOfferingExists(anyList());
    }

    @Test
    @DisplayName("Should store key and create order on first request")
    void createOrder_FirstTimeWithKey_Success() {
        when(idempotencyRepository.saveAndFlush(any())).thenReturn(new IdempotencyKey());
        when(orderRepository.save(any())).thenReturn(newOrderEntity);
        var result = orderService.createOrder(createDTO, KEY);

        // Assert
        assertFalse(result.isReplay());
        verify(idempotencyRepository).saveAndFlush(any()); // Initial lock
        verify(idempotencyRepository).save(any()); // Update with orderId
    }
    @Test
    @DisplayName("Should return existing order when duplicate request is sent (Replay)")
    void createOrder_DuplicateKey_ReturnsReplay() {
        String expectedHash = (String) ReflectionTestUtils.invokeMethod(orderService, "generateHash", createDTO);
        IdempotencyKey existingRecord = IdempotencyKey.builder()
                .key(KEY)
                .requestHash(expectedHash) // Matches exactly what the service will produce
                .orderId(newOrderEntity.getId())
                .build();

        when(idempotencyRepository.findById(KEY)).thenReturn(Optional.of(existingRecord));
        when(orderRepository.findById(newOrderEntity.getId())).thenReturn(Optional.of(newOrderEntity));

        var result = orderService.createOrder(createDTO, KEY);
        assertTrue(result.isReplay());
        assertEquals(newOrderEntity.getId(), result.dto().id());
    }

    @Test
    @DisplayName("Should throw Conflict if same key is used with different payload")
    void createOrder_SameKeyDifferentPayload_ThrowsConflict() {
        IdempotencyKey existingRecord = IdempotencyKey.builder()
                .key(KEY).requestHash("TOTALLY_DIFFERENT_HASH").build();

        when(idempotencyRepository.findById(KEY)).thenReturn(Optional.of(existingRecord));
        assertThrows(ResponseStatusException.class, () -> orderService.createOrder(createDTO, KEY));
    }
    @Test
    @DisplayName("Should throw error if first request is still processing (No orderId yet)")
    void createOrder_StillProcessing_ThrowsError() {
        String expectedHash = (String) ReflectionTestUtils.invokeMethod(orderService, "generateHash", createDTO);

        IdempotencyKey inProgressRecord = IdempotencyKey.builder()
                .key(KEY).requestHash(expectedHash)
                .orderId(null).build(); // Still null!

        when(idempotencyRepository.findById(KEY)).thenReturn(Optional.of(inProgressRecord));

        // Act & Assert
        var ex = assertThrows(ResponseStatusException.class, () -> orderService.createOrder(createDTO, KEY));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }
}
