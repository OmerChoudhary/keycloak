/**
 * Extracts the environment variables from the document, these variables are injected by Keycloak as a script tag, the contents of which can be parsed as JSON.
 */
export function getInjectedEnvironment<T>(): T {
  const element = document.getElementById("environment");

  if (!element?.textContent) {
    throw new Error("Environment variables not found.");
  }

  try {
    return JSON.parse(element.textContent);
  } catch (error) {
    throw new Error("Unable to parse environment variables.");
  }
}
