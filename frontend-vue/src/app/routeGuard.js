export function createRouteGuard(session) {
  return async function routeGuard(to) {
    await session.initialize();
    if (to.meta.public && session.authenticated && to.name === "auth") {
      return { name: "projects" };
    }
    if (!to.meta.public && !session.authenticated) {
      return { name: "auth", query: { redirect: to.fullPath } };
    }
    if (to.meta.capability && !session.hasCapability(to.meta.capability)) {
      return { name: "forbidden", query: { from: to.fullPath } };
    }
    return true;
  };
}
