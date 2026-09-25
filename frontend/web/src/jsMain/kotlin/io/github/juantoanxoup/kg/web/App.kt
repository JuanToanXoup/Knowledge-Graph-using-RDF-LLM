package io.github.juantoanxoup.kg.web

import io.github.juantoanxoup.kg.web.components.ui.Toaster
import io.github.juantoanxoup.kg.web.pages.Landing
import io.github.juantoanxoup.kg.web.pages.NotFound
import io.github.juantoanxoup.kg.web.pages.Workspace
import react.FC
import react.Props
import tanstack.query.core.QueryClient
import tanstack.react.query.QueryClientProvider
import tanstack.react.router.RootRouteOptions
import tanstack.react.router.RouteOptions
import tanstack.react.router.RouterOptions
import tanstack.react.router.RouterProvider
import tanstack.react.router.createRootRoute
import tanstack.react.router.createRoute
import tanstack.react.router.createRouter
import tanstack.router.core.ParamName
import tanstack.router.core.RoutePath

/** Route parameter that carries the graph identifier (`/workspace/:graphId`). */
val GRAPH_ID_PARAM = ParamName("graphId")

private val rootRoute = createRootRoute(RootRouteOptions())

/** Routes as declared in src/App.tsx: `/`, `/workspace`, `/workspace/:graphId`, and a catch-all. */
private val landingRoute =
    createRoute(RouteOptions(getParentRoute = { rootRoute }, path = RoutePath("/"), component = Landing))
private val workspaceRoute =
    createRoute(RouteOptions(getParentRoute = { rootRoute }, path = RoutePath("/workspace"), component = Workspace))
private val workspaceGraphRoute =
    createRoute(
        RouteOptions(
            getParentRoute = { rootRoute },
            path = RoutePath("/workspace/", GRAPH_ID_PARAM),
            component = Workspace,
        ),
    )

// Not named `router`: inside `RouterProvider { }` that name resolves to the props field, not this value.
private val appRouter =
    createRouter(
        RouterOptions(
            routeTree = rootRoute.apply { addChildren(arrayOf(landingRoute, workspaceRoute, workspaceGraphRoute)) },
            defaultNotFoundComponent = NotFound,
        ),
    )

private val queryClient = QueryClient()

val App =
    FC<Props> {
        QueryClientProvider {
            client = queryClient
            Toaster()
            RouterProvider { router = appRouter }
        }
    }
