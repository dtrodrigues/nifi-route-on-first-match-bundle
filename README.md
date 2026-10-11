# RouteOnFirstMatch for Apache NiFi

[![CI](https://github.com/dtrodrigues/nifi-route-on-first-match-bundle/actions/workflows/ci.yml/badge.svg?branch=nifi-1.x)](https://github.com/dtrodrigues/nifi-route-on-first-match-bundle/actions/workflows/ci.yml)

> **This is the NiFi 1.x branch.** For NiFi 2.x, use the
> [`main` branch](https://github.com/dtrodrigues/nifi-route-on-first-match-bundle/tree/main).

A NiFi processor that sends each FlowFile to the **first** route whose condition is true.
You set the order the routes are checked in by dragging them in the processor's Advanced UI.

NiFi's built-in `RouteOnAttribute` either clones a FlowFile to every matching route or requires all
conditions to match. When conditions overlap and only the highest-priority one should win, you
otherwise end up chaining several `RouteOnAttribute` processors. RouteOnFirstMatch does it in one step.

## How it works

- Each user-defined property is a route. Its name becomes a relationship, and its value is an
  Expression Language condition that returns `true` or `false`.
- Routes are evaluated in the order you set by dragging them in the processor's **Advanced** UI,
  highest priority first. With more than one route, the processor is invalid until every route,
  including ones added in the configuration dialog, has been placed there.
- Evaluation stops at the first match. A FlowFile is never cloned.
- FlowFiles that match nothing go to `unmatched`.
- The chosen route is written to the `RouteOnFirstMatch.Route` attribute.

Example:

| Property           | Value                           |
|--------------------|---------------------------------|
| `urgent`           | `${priority:equals('urgent')}`  |
| `large`            | `${fileSize:gt(1000000)}`       |

With `urgent` dragged above `large`, a FlowFile that is both urgent and large goes only to `urgent`.
Drag `large` above `urgent` and it goes only to `large`.

### The order is set only in the Advanced UI

Right-click the processor and choose **Advanced**. The routes appear as a numbered list:

- drag a route by its handle to change its priority;
- click a route to edit its name or condition;
- use **+** to add a route, and a route's menu to delete it.

Every change is saved to the processor right away. The processor must be stopped to make changes.

The order is stored in the processor's annotation data, which the configuration dialog does not show,
so it cannot be seen or changed there. A route added in the configuration dialog has no place in the order,
so the processor is invalid until you open **Advanced** and either drag it into place or click
**Accept order**, which keeps it where it is shown, at the end. A processor with only one route needs no order.

Outside the browser, the order is the processor's `annotationData`, for example
`{"evaluationOrder": ["urgent", "large"]}`, in the REST API and in flow definitions.

## Compatibility

Requires NiFi 1.x running on Java 8 or later. The NAR is built against the NiFi version in the root `pom.xml`
(currently 1.28.1), and its versions end in `-nifi1` so they never collide with the NiFi 2.x builds from `main`.
The processor, its routes and the saved order are the same on both branches, so a flow upgraded from NiFi 1.x to 2.x
keeps its routes and order once the NiFi 2.x NAR is installed.

To target a different NiFi 1.x release, change both the parent version and `nifi.version` in the root `pom.xml`.

The Advanced UI keeps the NiFi 2.x look, in its light theme, rather than NiFi 1.x's.
`./update-nifi-theme.sh` needs a NiFi 2.x version on this branch, for example `./update-nifi-theme.sh 2.12.0`.

## Build

Requires Java 17 and Maven 3.6 or later. The NiFi 1.x parent pom refuses to build on Java 21 or later. Maven downloads Node.js into `target/` to build the Advanced UI.

```shell
mvn clean install
```

The NAR is written to `nifi-route-on-first-match-nar/target/nifi-route-on-first-match-nar-<version>.nar`.

## Install

Copy the NAR into NiFi's `lib/` directory and restart NiFi.
Or drop it into the auto-load directory (`extensions/` by default), which needs no restart the first time.

To replace an installed NAR with one of the same version, such as a new `-SNAPSHOT` build, copy it over the old one
and restart NiFi. NiFi does not reload a NAR whose version is already loaded.

## Develop the Advanced UI

The Angular app is in `nifi-route-on-first-match-ui/src/main/frontend` and needs Node.js 24.

```shell
cd nifi-route-on-first-match-ui/src/main/frontend
npm ci
npm test           # unit tests
npm start          # dev server on http://localhost:4205, proxies API calls to NiFi at https://localhost:8443
```

To rebuild only the Java modules and reuse an existing frontend build, use:

```shell
mvn install -Dskip.installnodenpm -Dskip.npm
```

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Apache NiFi and NiFi are trademarks of The Apache Software Foundation. This project is not affiliated
with or endorsed by The Apache Software Foundation.
