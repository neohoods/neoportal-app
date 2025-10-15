package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.MonthlyOccupancy;
import com.neohoods.portal.platform.model.OccupancyCalendarDay;
import com.neohoods.portal.platform.model.SpaceStatistics;
import com.neohoods.portal.platform.model.SpaceStatisticsPeriod;
import com.neohoods.portal.platform.model.TopUser;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

@Service
@Transactional(readOnly = true)
public class SpaceStatisticsService {

        @Autowired
        private ReservationRepository reservationRepository;

        @Autowired
        private SpaceRepository spaceRepository;

        /**
         * Calculate comprehensive statistics for a space in a date range
         */
        public SpaceStatistics calculateSpaceStatistics(UUID spaceId, LocalDate startDate, LocalDate endDate) {
                // Get space information
                SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);
                if (space == null) {
                        throw new CodedErrorException(CodedError.SPACE_NOT_FOUND, "spaceId", spaceId);
                }

                // Get reservations for manual calculation
                List<ReservationEntity> reservations = reservationRepository.findReservationsForStatistics(spaceId,
                                startDate,
                                endDate);

                // Calculate basic metrics
                Long totalReservations = (long) reservations.size();
                // Revenue only includes confirmed/active/completed reservations (exclude
                // PENDING_PAYMENT)
                BigDecimal totalRevenue = reservations.stream()
                                .filter(r -> r.getStatus() != ReservationStatusForEntity.PENDING_PAYMENT)
                                .map(ReservationEntity::getTotalPrice)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calculate total days booked and average duration manually
                long totalDaysBooked = 0;
                double averageReservationDuration = 0.0;
                if (!reservations.isEmpty()) {
                        for (ReservationEntity reservation : reservations) {
                                long days = java.time.temporal.ChronoUnit.DAYS.between(reservation.getStartDate(),
                                                reservation.getEndDate()) + 1;
                                totalDaysBooked += days;
                        }
                        averageReservationDuration = (double) totalDaysBooked / reservations.size();
                }

                // Calculate occupancy rate
                long totalDaysInPeriod = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
                double occupancyRate = totalDaysInPeriod > 0 ? (double) totalDaysBooked / totalDaysInPeriod * 100.0
                                : 0.0;

                // Get top users with details (limit to 5)
                Pageable topUsersPageable = PageRequest.of(0, 5);
                List<Object[]> topUsersData = reservationRepository.getTopUsersWithDetails(spaceId, startDate,
                                endDate,
                                topUsersPageable);
                List<TopUser> topUsers = topUsersData.stream()
                                .map(row -> {
                                        UUID userId = (UUID) row[0];
                                        TopUser topUser = new TopUser();
                                        topUser.userId(userId.toString());
                                        topUser.userName((String) row[1] + " " + (String) row[2]);
                                        topUser.userEmail((String) row[3]);
                                        topUser.reservationCount(((Number) row[4]).intValue());
                                        topUser.totalSpent(((Number) row[5]).doubleValue());

                                        // Calculate total days manually
                                        List<ReservationEntity> userReservations = reservationRepository
                                                        .findReservationsByUserAndSpace(spaceId, userId, startDate,
                                                                        endDate);
                                        int totalDays = userReservations.stream()
                                                        .mapToInt(reservation -> {
                                                                long days = java.time.temporal.ChronoUnit.DAYS.between(
                                                                                reservation.getStartDate(),
                                                                                reservation.getEndDate()) + 1;
                                                                return (int) days;
                                                        })
                                                        .sum();
                                        topUser.totalDays(totalDays);

                                        return topUser;
                                })
                                .toList();

                // Build period
                SpaceStatisticsPeriod period = SpaceStatisticsPeriod.builder()
                                .startDate(startDate)
                                .endDate(endDate)
                                .build();

                // Calculate occupancy calendar
                List<OccupancyCalendarDay> occupancyCalendar = calculateOccupancyCalendar(reservations, startDate,
                                endDate);

                // Calculate monthly occupancy
                List<MonthlyOccupancy> monthlyOccupancy = calculateMonthlyOccupancy(reservations, startDate, endDate);

                // Build and return statistics
                return SpaceStatistics.builder()
                                .spaceId(spaceId)
                                .spaceName(space.getName())
                                .totalReservations(totalReservations.intValue())
                                .totalRevenue(totalRevenue.doubleValue())
                                .occupancyRate(occupancyRate)
                                .totalDaysBooked((int) totalDaysBooked)
                                .averageReservationDuration(averageReservationDuration)
                                .topUsers(topUsers)
                                .period(period)
                                .occupancyCalendar(occupancyCalendar)
                                .monthlyOccupancy(monthlyOccupancy)
                                .build();
        }

        /**
         * Calculate daily occupancy calendar for the given period
         */
        private List<OccupancyCalendarDay> calculateOccupancyCalendar(List<ReservationEntity> reservations,
                        LocalDate startDate, LocalDate endDate) {
                List<OccupancyCalendarDay> occupancyCalendar = new ArrayList<>();

                // Create a map to track reservations by date for easier lookup
                Map<LocalDate, ReservationEntity> reservationsByDate = new HashMap<>();
                for (ReservationEntity reservation : reservations) {
                        LocalDate reservationStart = reservation.getStartDate();
                        LocalDate reservationEnd = reservation.getEndDate();

                        // Ensure we don't go outside our period
                        LocalDate actualStart = reservationStart.isBefore(startDate) ? startDate : reservationStart;
                        LocalDate actualEnd = reservationEnd.isAfter(endDate) ? endDate : reservationEnd;

                        // Mark each day in the reservation
                        LocalDate day = actualStart;
                        while (!day.isAfter(actualEnd)) {
                                reservationsByDate.put(day, reservation);
                                day = day.plusDays(1);
                        }
                }

                // Create calendar entries for each day in the period
                LocalDate currentDate = startDate;
                while (!currentDate.isAfter(endDate)) {
                        ReservationEntity reservation = reservationsByDate.get(currentDate);
                        boolean isOccupied = reservation != null;

                        OccupancyCalendarDay day = OccupancyCalendarDay.builder()
                                        .date(currentDate)
                                        .isOccupied(isOccupied)
                                        .reservationId(isOccupied ? reservation.getId().toString() : null)
                                        .userName(isOccupied ? getUserNameForReservation(reservation) : null)
                                        .build();

                        occupancyCalendar.add(day);
                        currentDate = currentDate.plusDays(1);
                }

                return occupancyCalendar;
        }

        /**
         * Calculate monthly occupancy statistics for the given period
         */
        private List<MonthlyOccupancy> calculateMonthlyOccupancy(List<ReservationEntity> reservations,
                        LocalDate startDate, LocalDate endDate) {
                List<MonthlyOccupancy> monthlyOccupancy = new ArrayList<>();

                // Group reservations by month
                Map<YearMonth, List<ReservationEntity>> reservationsByMonth = reservations.stream()
                                .collect(Collectors
                                                .groupingBy(reservation -> YearMonth.from(reservation.getStartDate())));

                // Calculate occupancy for each month
                YearMonth currentMonth = YearMonth.from(startDate);
                YearMonth endMonth = YearMonth.from(endDate);

                while (!currentMonth.isAfter(endMonth)) {
                        List<ReservationEntity> monthReservations = reservationsByMonth.getOrDefault(currentMonth,
                                        new ArrayList<>());

                        // Calculate total days in the month
                        int daysInMonth = currentMonth.lengthOfMonth();

                        // Calculate total days booked in the month
                        long totalDaysBooked = 0;
                        for (ReservationEntity reservation : monthReservations) {
                                LocalDate reservationStart = reservation.getStartDate();
                                LocalDate reservationEnd = reservation.getEndDate();

                                // Ensure we stay within the month bounds
                                LocalDate monthStart = currentMonth.atDay(1);
                                LocalDate monthEnd = currentMonth.atEndOfMonth();

                                LocalDate actualStart = reservationStart.isBefore(monthStart) ? monthStart
                                                : reservationStart;
                                LocalDate actualEnd = reservationEnd.isAfter(monthEnd) ? monthEnd : reservationEnd;

                                if (!actualStart.isAfter(actualEnd)) {
                                        long days = java.time.temporal.ChronoUnit.DAYS.between(actualStart, actualEnd)
                                                        + 1;
                                        totalDaysBooked += days;
                                }
                        }

                        // Create monthly occupancy entry
                        MonthlyOccupancy monthOccupancy = MonthlyOccupancy.builder()
                                        .month(currentMonth.toString())
                                        .daysOccupied((int) totalDaysBooked)
                                        .daysAvailable(daysInMonth)
                                        .build();

                        monthlyOccupancy.add(monthOccupancy);
                        currentMonth = currentMonth.plusMonths(1);
                }

                return monthlyOccupancy;
        }

        /**
         * Helper method to get user name for a reservation
         */
        private String getUserNameForReservation(ReservationEntity reservation) {
                try {
                        com.neohoods.portal.platform.entities.UserEntity user = reservation.getUser();
                        if (user != null && user.getFirstName() != null && user.getLastName() != null) {
                                return user.getFirstName() + " " + user.getLastName();
                        } else if (user != null && user.getUsername() != null) {
                                return user.getUsername();
                        } else {
                                return "User " + user.getId().toString().substring(0, 8);
                        }
                } catch (Exception e) {
                        // Fallback in case of lazy loading issues
                        return "User";
                }
        }
}
