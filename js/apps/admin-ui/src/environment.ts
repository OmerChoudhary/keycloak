import { getInjectedEnvironment } from "@keycloak/keycloak-ui-shared";

export type Environment = {
  /** The realm used to authenticate the user to the Admin Console. */
  loginRealm: string;
  /** The identifier of the client used to authenticate the user to the Admin Console. */
  clientId: string;
  /** The URL to the root of the auth server. */
  authServerUrl: string;
  /** The URL to the path of the auth server where client requests can be sent. */
  authUrl: string;
  /** The URL to the base of the Admin UI. */
  consoleBaseUrl: string;
  /** The URL to resources such as the files in the `public` directory. */
  resourceUrl: string;
  /** The name of the master realm. */
  masterRealm: string;
  /** The version hash of the auth server. */
  resourceVersion: string;
  /** Indicates the src for the Brand image */
  logo: string;
  /** Indicates the url to be followed when Brand image is clicked */
  logoUrl: string;
};

export const environment = getInjectedEnvironment<Environment>();
