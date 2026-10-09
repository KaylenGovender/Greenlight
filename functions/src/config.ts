/**
 * Region for every function. Must match FUNCTIONS_REGION in
 * app/src/main/java/com/greenlight/app/data/firebase/FirebaseEnv.kt, and your Firestore
 * database location (Firestore triggers must run in the database's region). See README step 6.
 */
export const REGION = "africa-south1";

/** Pairing codes expire after this many minutes. */
export const PAIRING_TTL_MINUTES = 15;

/** Time zone used for reminders and notification wording. */
export const TIME_ZONE = "Africa/Johannesburg";

/**
 * Cloud Scheduler isn't available in africa-south1, so the timed reminder functions run here.
 * They only read and write Firestore over the network, so the region only affects where they run.
 */
export const SCHEDULER_REGION = "europe-west1";

/**
 * Accounts allowed to use the admin tools (maintenance screen + impersonation). Checked on the
 * server against Google's verified email claim; the app only uses it to show/hide menu items.
 */
export const ADMIN_EMAILS = ["kaylengovender@gmail.com"];
