import { getInjectedEnvironment } from "@keycloak/keycloak-ui-shared";

export type Feature = {
  isRegistrationEmailAsUsername: boolean;
  isEditUserNameAllowed: boolean;
  isInternationalizationEnabled: boolean;
  isLinkedAccountsEnabled: boolean;
  isEventsEnabled: boolean;
  isMyResourcesEnabled: boolean;
  isTotpConfigured: boolean;
  deleteAccountAllowed: boolean;
  updateEmailFeatureEnabled: boolean;
  updateEmailActionEnabled: boolean;
  isViewGroupsEnabled: boolean;
};

export type Environment = {
  /** The URL to the root of the auth server. */
  authUrl: string;
  /** The URL to the root of the account console. */
  baseUrl: string;
  /** The realm used to authenticate the user to the Account Console. */
  realm: string;
  /** The identifier of the client used to authenticate the user to the Account Console. */
  clientId: string;
  /** The URL to resources such as the files in the `public` directory. */
  resourceUrl: string;
  /** Indicates the src for the Brand image */
  logo: string;
  /** Indicates the url to be followed when Brand image is clicked */
  logoUrl: string;
  /** The locale of the user */
  locale: string;
  /** Feature flags */
  features: Feature;
  /** Name of the referrer application in the back link */
  referrerName?: string;
  /** UR to the referrer application in the back link */
  referrerUrl?: string;
};

export const environment = getInjectedEnvironment<Environment>();
