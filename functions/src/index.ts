import { initializeApp } from "firebase-admin/app";

initializeApp();

export {
  createCouple, previewCouple, acceptCouple, declineCouple, cancelCouple,
  createGroup, previewGroup, joinGroup, leaveGroup, resetGroupCode, cleanupExpiredPairings,
} from "./groups";
export { onGroupWatchSwipe, onGroupRestaurantSwipe, onGroupActivitySwipe } from "./matching";
export { onGroupPlanWritten, planReminders, groupSwipeReminders, onGroupMemberActivity, completePlan } from "./plans";
export {
  adminWhoAmI, adminListUsers, adminGetUser, adminCreateUser, adminUpdateUser, adminDeleteUser,
  adminListGroups, adminGetGroup, adminUpdateGroup, adminAddMember, adminRemoveMember, adminResetGroupCode,
  adminDeleteGroup, adminDeleteRecord, adminImpersonate, stopImpersonating, adminWipeData, adminDeleteEverything,
  adminSetAppConfig,
} from "./admin";
export { deleteMyAccount, exportMyData } from "./account";
export { checkAccess } from "./access";
export {
  adminStats, adminListAudit, adminGetAppConfig, adminSaveAppSettings, adminBroadcast,
  adminGetAccess, adminSetAccessMode, adminSetAccess,
} from "./console";
