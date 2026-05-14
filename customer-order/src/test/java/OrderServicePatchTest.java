import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.customer.order.service.client.CatalogClient;
import com.customer.order.service.database.embeddables.Customer;
import com.customer.order.service.database.embeddables.PaymentMethod;
import com.customer.order.service.database.embeddables.Site;
import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.enums.OrderCategory;
import com.customer.order.service.database.enums.OrderState;
import com.customer.order.service.database.enums.PaymentType;
import com.customer.order.service.database.repositories.CustomerOrderRepository;
import com.customer.order.service.database.repositories.IdempotencyRepository;
import com.customer.order.service.dto.OrderPatchDTO;
import com.customer.order.service.dto.OrderResponseDTO;
import com.customer.order.service.mappers.EntityToDtoMapping;
import com.customer.order.service.service.OrderService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
public class OrderServicePatchTest {
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private CatalogClient catalogClient;

    private EntityToDtoMapping mapper;

    private OrderService orderService;

    private UUID orderId;
    private CustomerOrder existingOrder;

    @BeforeEach
    void setUp() {
        this.mapper = new EntityToDtoMapping();
        this.orderService = new OrderService(this.orderRepository,
                this.idempotencyRepository,this.catalogClient,
                this.mapper);

        orderId = UUID.randomUUID();
        existingOrder = CustomerOrder.builder()
                .id(orderId)
                .category(OrderCategory.B2B)
                .customer(Customer.builder().id("CUST-001").build())
                .site(Site.builder().id("SITE-001").build())
                .paymentMethod(PaymentMethod.builder().paymentType(PaymentType.INVOICE).build())
                .state(OrderState.DRAFT).build();
    }
    @Test
    void patchOrder_ValidTransition_FromDraftToPreview() {
        OrderPatchDTO patch = new OrderPatchDTO(null, OrderState.PREVIEW.getValue(), null, null, null, null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        OrderResponseDTO result = orderService.patchOrder(orderId, patch);
        assertEquals(OrderState.PREVIEW, result.state());
    }

    @Test
    void patchOrder_InvalidTransition_ThrowsException() {
        // Arrange: DRAFT -> SUBMITTED is not allowed (must go to PREVIEW first)
        OrderPatchDTO patch = new OrderPatchDTO(null, "submitted", null, null, null, null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        assertThrows(ResponseStatusException.class, () -> orderService.patchOrder(orderId, patch));
    }

    @Test
    void patchOrder_SubmittedState_AllowStateChangeOnly() {

        existingOrder.setState(OrderState.SUBMITTED);
        OrderPatchDTO patch = new OrderPatchDTO(null, "confirmed", null, null,null, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        OrderResponseDTO result = orderService.patchOrder(orderId, patch);
        assertEquals(OrderState.CONFIRMED, result.state());
        //State is confirmed, should not allow any further modifications
        OrderPatchDTO patchInvalid = new OrderPatchDTO(OrderCategory.B2C.name(), "confirmed", null, null, null, null);
        assertThrows(IllegalStateException.class, () -> orderService.patchOrder(orderId, patchInvalid));

    }
    @Test
    void patchOrder_SubmittedState_AllowStateChangeOnlyII(){

        existingOrder.setState(OrderState.SUBMITTED);
        OrderPatchDTO patch = new OrderPatchDTO(OrderCategory.B2C.name(), "confirmed", null, null, null,null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
        assertThrows(ResponseStatusException.class, () -> orderService.patchOrder(orderId, patch));

    }


}
