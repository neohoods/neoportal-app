package com.neohoods.portal.platform.services.matrix.space;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of room invitation process
 */
public class MatrixRoomInvitationResult {
    private final List<String> newlyInvitedRooms;
    private final List<String> alreadyMemberOrInvitedRooms;

    public MatrixRoomInvitationResult() {
        this.newlyInvitedRooms = new ArrayList<>();
        this.alreadyMemberOrInvitedRooms = new ArrayList<>();
    }

    public void addNewlyInvited(String roomName) {
        newlyInvitedRooms.add(roomName);
    }

    public void addAlreadyMemberOrInvited(String roomName) {
        alreadyMemberOrInvitedRooms.add(roomName);
    }

    public List<String> getAllRooms() {
        List<String> all = new ArrayList<>(newlyInvitedRooms);
        all.addAll(alreadyMemberOrInvitedRooms);
        return all;
    }

    public List<String> getNewlyInvitedRooms() {
        return newlyInvitedRooms;
    }

    public List<String> getAlreadyMemberOrInvitedRooms() {
        return alreadyMemberOrInvitedRooms;
    }

    public int getTotalCount() {
        return newlyInvitedRooms.size() + alreadyMemberOrInvitedRooms.size();
    }
}

