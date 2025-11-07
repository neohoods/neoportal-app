import { Unit } from '../api-client/model/unit';
import { UnitMember, UnitMemberRoleEnum } from '../api-client/model/unitMember';
import { User } from '../api-client/model/user';
import unitsData from './units.json';

const mapUnit = (unit: any): Unit => {
  return {
    id: unit.id,
    name: unit.name,
    createdAt: unit.createdAt,
    updatedAt: unit.updatedAt,
    members: unit.members?.map((member: any): UnitMember => ({
      userId: member.userId,
      role: member.role as UnitMemberRoleEnum,
      joinedAt: member.joinedAt,
      user: member.user ? {
        id: member.user.id,
        username: member.user.username,
        firstName: member.user.firstName,
        lastName: member.user.lastName,
        email: member.user.email,
      } as User : undefined as any,
    })) || [],
  };
};

export const loadUnitsData = (): Unit[] => {
  return unitsData.map(mapUnit);
};

