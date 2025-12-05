package com.neohoods.portal.platform.services.matrix.space;

/**
 * Statistics collected during initialization
 */
public class MatrixInitializationStats {
    private int roomsCreated = 0;
    private int roomsExisting = 0;
    private int roomsErrors = 0;
    private int usersCreated = 0;
    private int usersUpdated = 0;
    private int usersErrors = 0;
    private int spaceInvitationsSent = 0;
    private int roomInvitationsSent = 0;
    private int pendingInvitations = 0;
    private int avatarsUpdated = 0;
    private int avatarsSkipped = 0;
    private int avatarsFailed = 0;

    public int getRoomsCreated() {
        return roomsCreated;
    }

    public void setRoomsCreated(int roomsCreated) {
        this.roomsCreated = roomsCreated;
    }

    public int getRoomsExisting() {
        return roomsExisting;
    }

    public void setRoomsExisting(int roomsExisting) {
        this.roomsExisting = roomsExisting;
    }

    public int getRoomsErrors() {
        return roomsErrors;
    }

    public void setRoomsErrors(int roomsErrors) {
        this.roomsErrors = roomsErrors;
    }

    public int getUsersCreated() {
        return usersCreated;
    }

    public void setUsersCreated(int usersCreated) {
        this.usersCreated = usersCreated;
    }

    public int getUsersUpdated() {
        return usersUpdated;
    }

    public void setUsersUpdated(int usersUpdated) {
        this.usersUpdated = usersUpdated;
    }

    public int getUsersErrors() {
        return usersErrors;
    }

    public void setUsersErrors(int usersErrors) {
        this.usersErrors = usersErrors;
    }

    public int getSpaceInvitationsSent() {
        return spaceInvitationsSent;
    }

    public void setSpaceInvitationsSent(int spaceInvitationsSent) {
        this.spaceInvitationsSent = spaceInvitationsSent;
    }

    public int getRoomInvitationsSent() {
        return roomInvitationsSent;
    }

    public void setRoomInvitationsSent(int roomInvitationsSent) {
        this.roomInvitationsSent = roomInvitationsSent;
    }

    public int getPendingInvitations() {
        return pendingInvitations;
    }

    public void setPendingInvitations(int pendingInvitations) {
        this.pendingInvitations = pendingInvitations;
    }

    public int getAvatarsUpdated() {
        return avatarsUpdated;
    }

    public void setAvatarsUpdated(int avatarsUpdated) {
        this.avatarsUpdated = avatarsUpdated;
    }

    public int getAvatarsSkipped() {
        return avatarsSkipped;
    }

    public void setAvatarsSkipped(int avatarsSkipped) {
        this.avatarsSkipped = avatarsSkipped;
    }

    public int getAvatarsFailed() {
        return avatarsFailed;
    }

    public void setAvatarsFailed(int avatarsFailed) {
        this.avatarsFailed = avatarsFailed;
    }
}

