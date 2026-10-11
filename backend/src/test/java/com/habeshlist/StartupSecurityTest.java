package com.habeshlist;

import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.repository.*;
import com.habeshlist.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupSecurityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    private DataSeeder seeder() {
        DataSeeder seeder = new DataSeeder(auth, users, mock(ShipmentService.class), mock(TripService.class),
                mock(ShipmentRepository.class), mock(TripRepository.class), mock(LuggageRepository.class),
                mock(LuggageService.class), mock(RecipientRepository.class), mock(AgentProfileRepository.class),
                mock(CommissionRepository.class), mock(TrackingEventService.class),
                new MockEnvironment().withProperty("spring.profiles.active", "production"), passwords);
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "");
        return seeder;
    }

    @Test
    void productionRejectsActiveDemoPasswordsInsteadOfSilentlyKeepingThem() {
        when(users.findAll()).thenReturn(List.of(User.builder().active(true).role(Role.ADMIN)
                .passwordHash(passwords.encode("Admin@123")).build()));
        assertThrows(IllegalStateException.class, () -> seeder().seed());
        verifyNoInteractions(auth);
    }

    @Test
    void productionRequiresUniqueInitialAdministratorCredentials() {
        when(users.findAll()).thenReturn(List.of());
        when(users.findAllByRole(Role.ADMIN)).thenReturn(List.of());
        DataSeeder seeder = seeder();
        assertThrows(IllegalStateException.class, seeder::seed);
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "owner@example.com");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "A-unique-bootstrap-password-2026");
        seeder.seed();
        verify(auth).createPrivilegedUser("Admin", "User", "owner@example.com", null,
                "A-unique-bootstrap-password-2026", Role.ADMIN);
    }

    @Test
    void productionCannotEnableDemoSeeding() {
        DataSeeder seeder = seeder();
        ReflectionTestUtils.setField(seeder, "demoDataEnabled", true);
        assertThrows(IllegalStateException.class, seeder::seed);
        verifyNoInteractions(auth);
    }
}
