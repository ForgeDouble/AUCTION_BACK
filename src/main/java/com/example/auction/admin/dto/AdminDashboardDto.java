package com.example.auction.admin.dto;

import java.util.List;

public class AdminDashboardDto {

    private AdminProfile admin;
    private OverviewStats stats;
    private List<HourlyPoint> todayHourlyUsers;
    private MoneyStats money;

    public AdminDashboardDto(AdminProfile admin, OverviewStats stats, List<HourlyPoint> todayHourlyUsers, MoneyStats money) {
        this.admin = admin;
        this.stats = stats;
        this.todayHourlyUsers = todayHourlyUsers;
        this.money = money;
    }

    public AdminProfile getAdmin() {
        return admin;
    }

    public OverviewStats getStats() {
        return stats;
    }

    public List<HourlyPoint> getTodayHourlyUsers() {
        return todayHourlyUsers;
    }

    public MoneyStats getMoney() {
        return money;
    }

    public static class AdminProfile {
        private String email;
        private String nick;
        private String role;

        public AdminProfile(String email, String nick, String role) {
            this.email = email;
            this.nick = nick;
            this.role = role;
        }

        public String getEmail() {
            return email;
        }

        public String getNick() {
            return nick;
        }

        public String getRole() {
            return role;
        }
    }

    public static class OverviewStats {
        private long todayNewUsers;
        private long todayNewAuctions;
        private long todayEndedAuctions;
        private long totalBids;
        private long ongoingAuctions;

        private long reports; // 신고(대기/처리중) 카운트
        private long realtimeUsers;
        private long todayActiveUsers;

        public OverviewStats(long todayNewUsers, long todayNewAuctions, long todayEndedAuctions,
                             long totalBids, long ongoingAuctions,
                             long reports, long realtimeUsers, long todayActiveUsers) {
            this.todayNewUsers = todayNewUsers;
            this.todayNewAuctions = todayNewAuctions;
            this.todayEndedAuctions = todayEndedAuctions;
            this.totalBids = totalBids;
            this.ongoingAuctions = ongoingAuctions;
            this.reports = reports;
            this.realtimeUsers = realtimeUsers;
            this.todayActiveUsers = todayActiveUsers;
        }

        public long getTodayNewUsers() {
            return todayNewUsers;
        }

        public long getTodayNewAuctions() {
            return todayNewAuctions;
        }

        public long getTodayEndedAuctions() {
            return todayEndedAuctions;
        }

        public long getTotalBids() {
            return totalBids;
        }

        public long getOngoingAuctions() {
            return ongoingAuctions;
        }

        public long getReports() {
            return reports;
        }

        public long getRealtimeUsers() {
            return realtimeUsers;
        }

        public long getTodayActiveUsers() {
            return todayActiveUsers;
        }
    }

    public static class HourlyPoint {
        private String time;
        private long users;

        public HourlyPoint(String time, long users) {
            this.time = time;
            this.users = users;
        }

        public String getTime() {
            return time;
        }

        public long getUsers() {
            return users;
        }
    }

    public static class MoneyStats {
        private long todayTotalAmount;
        private long monthlyAverageAmount;

        public MoneyStats(long todayTotalAmount, long monthlyAverageAmount) {
            this.todayTotalAmount = todayTotalAmount;
            this.monthlyAverageAmount = monthlyAverageAmount;
        }

        public long getTodayTotalAmount() {
            return todayTotalAmount;
        }

        public long getMonthlyAverageAmount() {
            return monthlyAverageAmount;
        }
    }
}

