(function () {
  if (globalThis.__stremioRuntimeLoaded) {
    return;
  }
  globalThis.__stremioRuntimeLoaded = true;

  const CONTRACT_VERSION = 1;
  const CATALOG_REFRESH_INTERVAL_MS = 1500;

  const runtime = {
    coreInitPromise: null,
    coreReady: false,
    initStartedAtMs: 0,
    initStatus: "idle",
    initErrorMessage: null,
    eventQueue: [],
    catalogByType: {
      movie: [],
      series: [],
    },
    catalogRefreshAt: {
      movie: 0,
      series: 0,
    },
    catalogRefreshTimers: {
      movie: null,
      series: null,
    },
    metaDeepLinks: Object.create(null),
    metaTitles: Object.create(null),
    dispatchSuccessCount: 0,
    dispatchFailureCount: 0,
    lastDispatchError: null,
    searchModelLoaded: false,
    searchQuery: "",
    searchTriggeredAtMs: 0,
    activeMetaId: null,
    player: {
      streamId: null,
      progressMs: 0,
      durationMs: null,
    },
    libraryReason: "init",
  };

  const now = function () {
    return Date.now();
  };

  const envelope = function (type, payload) {
    return {
      type: type,
      version: CONTRACT_VERSION,
      payload: payload || {},
      timestampMs: now(),
    };
  };

  const snapshot = function (scope, data) {
    return {
      scope: scope,
      version: CONTRACT_VERSION,
      updatedAtMs: now(),
      data: data,
    };
  };

  const runtimeResponse = function (events, extra) {
    const base = {
      events: events || [],
    };
    if (extra && typeof extra === "object") {
      const keys = Object.keys(extra);
      for (let index = 0; index < keys.length; index += 1) {
        base[keys[index]] = extra[keys[index]];
      }
    }
    return base;
  };

  const pushEvent = function (type, payload) {
    runtime.eventQueue.push(envelope(type, payload || {}));
  };

  const drainEvents = function () {
    if (runtime.eventQueue.length === 0) {
      return [];
    }
    const drained = runtime.eventQueue.slice(0);
    runtime.eventQueue.length = 0;
    return drained;
  };
  const flushTimers = function () {
    try {
      if (typeof globalThis.__flushTimers === "function") {
        globalThis.__flushTimers();
      }
    } catch (_error) { }
  };

  const asArray = function (value) {
    return Array.isArray(value) ? value : [];
  };

  const safeGetState = function (field) {
    try {
      if (typeof globalThis.getState !== "function") {
        return null;
      }
      return globalThis.getState(field);
    } catch (_error) {
      return null;
    }
  };

  const coreDispatch = function (action, fieldOrLocationHash, maybeLocationHash) {
    let field = null;
    let locationHash = "";
    if (typeof maybeLocationHash === "undefined" && (typeof fieldOrLocationHash === "string" || fieldOrLocationHash == null)) {
      locationHash = typeof fieldOrLocationHash === "string" ? fieldOrLocationHash : "";
    } else {
      field = fieldOrLocationHash == null ? null : fieldOrLocationHash;
      locationHash = typeof maybeLocationHash === "string" ? maybeLocationHash : "";
    }

    if (typeof globalThis.dispatch !== "function") {
      runtime.dispatchFailureCount += 1;
      runtime.lastDispatchError = "dispatch function missing";
      return false;
    }
    try {
      globalThis.dispatch(action, field, locationHash || "");
      runtime.dispatchSuccessCount += 1;
      runtime.lastDispatchError = null;
      return true;
    } catch (error) {
      runtime.dispatchFailureCount += 1;
      runtime.lastDispatchError = (function () {
        if (error == null) {
          return "unknown dispatch error";
        }
        if (typeof error === "string") {
          return error;
        }
        if (typeof error.message === "string" && error.message !== "") {
          return error.message;
        }
        try {
          return JSON.stringify(error);
        } catch (_jsonError) {
          return String(error);
        }
      })();
      return false;
    }
  };

  const hasReadyContent = function (content) {
    return !!content && typeof content === "object" && content.type === "Ready" && Array.isArray(content.content);
  };

  const extractIdsFromContent = function (content) {
    const items = [];
    if (!content || typeof content !== "object") {
      return items;
    }

    let source = [];
    if (Array.isArray(content)) {
      source = content;
    } else if (Array.isArray(content.content)) {
      source = content.content;
    }

    for (let index = 0; index < source.length; index += 1) {
      const item = source[index];
      if (!item || typeof item !== "object") {
        continue;
      }
      const id = typeof item.id === "string" && item.id.trim() !== ""
        ? item.id.trim()
        : (typeof item.name === "string" ? item.name.trim() : "");
      if (id !== "") {
        items.push(id);
      }

      if (item.deepLinks && typeof item.deepLinks === "object") {
        const deepLink = item.deepLinks.metaDetailsStreams || item.deepLinks.metaDetailsVideos;
        if (typeof deepLink === "string" && deepLink.trim() !== "" && id !== "") {
          runtime.metaDeepLinks[id] = deepLink;
        }
      }
      if (typeof item.name === "string" && item.name.trim() !== "" && id !== "") {
        runtime.metaTitles[id] = item.name.trim();
      }
    }

    return items;
  };

  const scheduleCatalogRefresh = function (type) {
    if (runtime.catalogRefreshTimers[type] != null) {
      return;
    }
    runtime.catalogRefreshTimers[type] = setTimeout(function () {
      runtime.catalogRefreshTimers[type] = null;
      refreshCatalogType(type, true);
    }, 1400);
  };

  const refreshCatalogType = function (type, force) {
    const refreshAt = runtime.catalogRefreshAt[type] || 0;
    if (!force && now() - refreshAt < CATALOG_REFRESH_INTERVAL_MS) {
      return;
    }
    runtime.catalogRefreshAt[type] = now();

    coreDispatch(
      {
        action: "Load",
        args: {
          model: "CatalogsWithExtra",
          args: { type: type },
        },
      },
      ""
    );

    coreDispatch(
      {
        action: "CatalogsWithExtra",
        args: {
          action: "LoadRange",
          args: {
            start: 0,
            end: 40,
          },
        },
      },
      ""
    );

    const board = safeGetState("board");
    const catalogs = board && Array.isArray(board.catalogs) ? board.catalogs : [];
    runtime.catalogByType[type] = catalogs;

    for (let index = 0; index < catalogs.length; index += 1) {
      extractIdsFromContent(catalogs[index] && catalogs[index].content);
    }

    scheduleCatalogRefresh(type);
  };

  const pickCatalogRow = function (type, fallbackTitle) {
    const catalogs = asArray(runtime.catalogByType[type]);
    if (catalogs.length === 0) {
      return {
        id: "popular_" + type,
        title: fallbackTitle,
        items: [],
      };
    }

    let selected = null;
    for (let index = 0; index < catalogs.length; index += 1) {
      const catalog = catalogs[index];
      if (!catalog || typeof catalog !== "object") {
        continue;
      }
      if (catalog.id === "top") {
        selected = catalog;
        break;
      }
      const ids = extractIdsFromContent(catalog.content);
      if (ids.length > 0) {
        selected = catalog;
        break;
      }
      if (!selected) {
        selected = catalog;
      }
    }

    if (!selected) {
      return {
        id: "popular_" + type,
        title: fallbackTitle,
        items: [],
      };
    }

    const selectedIds = extractIdsFromContent(selected.content);
    return {
      id: "popular_" + type,
      title: fallbackTitle,
      items: selectedIds,
    };
  };

  const buildCatalogState = function () {
    const rawBoard = safeGetState("board");
    const boardCatalogCount = rawBoard && Array.isArray(rawBoard.catalogs) ? rawBoard.catalogs.length : -1;
    refreshCatalogType("movie", false);
    refreshCatalogType("series", false);

    const movieRow = pickCatalogRow("movie", "Popular - Movie");
    const seriesRow = pickCatalogRow("series", "Popular - Series");

    const rows = [movieRow, seriesRow];
    const featuredIds = movieRow.items.slice(0, 20);

    return {
      coreReady: runtime.coreReady,
      initStartedAtMs: runtime.initStartedAtMs,
      initStatus: runtime.initStatus,
      initErrorMessage: runtime.initErrorMessage,
      dispatchSuccessCount: runtime.dispatchSuccessCount,
      dispatchFailureCount: runtime.dispatchFailureCount,
      lastDispatchError: runtime.lastDispatchError,
      hasBoardState: !!rawBoard,
      boardCatalogCount: boardCatalogCount,
      featuredIds: featuredIds,
      rows: rows,
    };
  };

  const ensureSearchModelLoaded = function () {
    if (runtime.searchModelLoaded) {
      return;
    }
    runtime.searchModelLoaded = coreDispatch(
      {
        action: "Load",
        args: {
          model: "LocalSearch",
        },
      },
      ""
    );
  };

  const triggerSearch = function (query) {
    ensureSearchModelLoaded();

    runtime.searchTriggeredAtMs = now();
    coreDispatch(
      {
        action: "Search",
        args: {
          action: "Search",
          args: {
            searchQuery: query,
            maxResults: 60,
          },
        },
      },
      ""
    );
  };

  const buildSearchState = function () {
    ensureSearchModelLoaded();

    if (runtime.searchQuery !== "" && now() - runtime.searchTriggeredAtMs > 1200) {
      triggerSearch(runtime.searchQuery);
    }

    const state = safeGetState("search") || {};
    const catalogs = asArray(state.catalogs);
    const results = [];

    for (let index = 0; index < catalogs.length; index += 1) {
      const ids = extractIdsFromContent(catalogs[index] && catalogs[index].content);
      for (let idIndex = 0; idIndex < ids.length; idIndex += 1) {
        if (results.indexOf(ids[idIndex]) < 0) {
          results.push(ids[idIndex]);
        }
      }
    }

    return {
      query: runtime.searchQuery,
      results: results,
    };
  };

  const buildMetaState = function () {
    const meta = safeGetState("meta_details") || {};
    const metaItem = meta.metaItem && typeof meta.metaItem === "object" ? meta.metaItem : null;

    const activeMetaId = runtime.activeMetaId
      || (meta.selected && typeof meta.selected.id === "string" ? meta.selected.id : null);

    const title = metaItem && typeof metaItem.name === "string"
      ? metaItem.name
      : (activeMetaId && runtime.metaTitles[activeMetaId] ? runtime.metaTitles[activeMetaId] : null);

    const subtitle = metaItem && typeof metaItem.description === "string"
      ? metaItem.description
      : null;

    return {
      activeMetaId: activeMetaId,
      title: title,
      subtitle: subtitle,
    };
  };

  const buildSessionState = function () {
    const ctx = safeGetState("ctx") || {};
    const profile = ctx.profile && typeof ctx.profile === "object" ? ctx.profile : null;
    const auth = profile && profile.auth && typeof profile.auth === "object" ? profile.auth : null;
    const user = auth && auth.user && typeof auth.user === "object" ? auth.user : null;

    const userId = user && typeof user.id === "string"
      ? user.id
      : (user && typeof user.email === "string"
        ? user.email
        : (auth && typeof auth.key === "string" ? auth.key : null));

    return {
      isAuthenticated: !!auth,
      userId: userId,
    };
  };

  const buildLibraryState = function () {
    const library = safeGetState("library") || {};
    const catalog = library.catalog && typeof library.catalog === "object" ? library.catalog : null;
    const changedItemIds = extractIdsFromContent(catalog && catalog.content);

    const itemCount = catalog && typeof catalog.total === "number"
      ? catalog.total
      : changedItemIds.length;

    return {
      itemCount: itemCount,
      changedItemIds: changedItemIds,
      reason: runtime.libraryReason,
    };
  };

  const buildAddonsState = function () {
    const ctx = safeGetState("ctx") || {};
    const profile = ctx.profile && typeof ctx.profile === "object" ? ctx.profile : null;
    const addons = profile ? asArray(profile.addons) : [];

    const installed = [];
    for (let index = 0; index < addons.length; index += 1) {
      const item = addons[index];
      const manifest = item && item.manifest && typeof item.manifest === "object" ? item.manifest : null;
      const id = manifest && typeof manifest.id === "string" ? manifest.id : null;
      if (id && installed.indexOf(id) < 0) {
        installed.push(id);
      }
    }

    return {
      installed: installed,
    };
  };

  const buildSettingsState = function () {
    const streamingServer = safeGetState("streaming_server") || {};
    const settings = streamingServer.settings && typeof streamingServer.settings === "object"
      ? streamingServer.settings
      : {};

    const values = {};
    values.transportUrl = streamingServer.selected && streamingServer.selected.transportUrl
      ? String(streamingServer.selected.transportUrl)
      : "";
    values.baseUrl = streamingServer.baseUrl ? String(streamingServer.baseUrl) : "";

    if (settings.type && typeof settings.type === "string") {
      values.settingsState = settings.type;
    }

    return values;
  };

  const buildPlayerState = function () {
    const playerState = safeGetState("player") || {};
    const stream = playerState.stream && typeof playerState.stream === "object" ? playerState.stream : null;

    return {
      streamId: runtime.player.streamId
        || (stream && typeof stream.id === "string" ? stream.id : null),
      progressMs: runtime.player.progressMs,
    };
  };

  const emitDerivedEvents = function (rawEvent) {
    if (!rawEvent || typeof rawEvent !== "object") {
      return;
    }

    if (rawEvent.name === "NewState" && Array.isArray(rawEvent.args)) {
      const field = rawEvent.args[0];
      if (field === "ctx") {
        const session = buildSessionState();
        pushEvent("auth.changed", {
          isAuthenticated: session.isAuthenticated,
          userId: session.userId,
        });
      }

      if (field === "library") {
        const library = buildLibraryState();
        pushEvent("library.changed", {
          itemCount: library.itemCount,
          changedItemIds: library.changedItemIds,
          reason: library.reason || "unknown",
        });
      }

      if (field === "board") {
        runtime.catalogRefreshAt.movie = 0;
        runtime.catalogRefreshAt.series = 0;
      }
    }
  };

  globalThis.onCoreEvent = function (event) {
    pushEvent("runtime.raw", { rawEvent: event });
    emitDerivedEvents(event);
  };

  const ensureCoreInitialized = function () {
    if (runtime.coreInitPromise) {
      return runtime.coreInitPromise;
    }

    if (typeof globalThis.init !== "function") {
      pushEvent("runtime.error", {
        code: "core_worker_missing",
        message: "Stremio core worker bootstrap is unavailable.",
        recoverable: false,
      });
      runtime.coreInitPromise = Promise.resolve();
      return runtime.coreInitPromise;
    }

    runtime.coreInitPromise = Promise.resolve(
      globalThis.init({
        appVersion: globalThis.app_version || "0.1.1-tv",
        shellVersion: globalThis.shell_version || "0.1.1-tv",
      })
    ).then(function () {
      runtime.coreReady = true;
      runtime.initStatus = "ready";
      runtime.initErrorMessage = null;
      pushEvent("runtime.initialized", { source: "stremio-core" });

      coreDispatch({ action: "Link", args: { action: "ReadData" } }, "");
      refreshCatalogType("movie", true);
      refreshCatalogType("series", true);
      ensureSearchModelLoaded();
    }).catch(function (error) {
      runtime.initStatus = "failed";
      runtime.initErrorMessage = String(error);
      pushEvent("runtime.error", {
        code: "core_init_failed",
        message: String(error),
        recoverable: false,
      });
    });
    runtime.initStartedAtMs = now();
    runtime.initStatus = "pending";
    runtime.initErrorMessage = null;

    return runtime.coreInitPromise;
  };

  const handleCustomAction = function (customType, payload) {
    if (customType === "updateSearch") {
      runtime.searchQuery = typeof payload.query === "string" ? payload.query.trim() : "";
      triggerSearch(runtime.searchQuery);
      return;
    }

    if (customType === "selectMeta") {
      runtime.activeMetaId = typeof payload.id === "string" && payload.id.trim() !== ""
        ? payload.id.trim()
        : null;

      if (runtime.activeMetaId && runtime.metaDeepLinks[runtime.activeMetaId]) {
        globalThis.location.hash = runtime.metaDeepLinks[runtime.activeMetaId];
        coreDispatch({ action: "Link", args: { action: "ReadData" } }, globalThis.location.hash);
      }
      return;
    }

    if (customType.indexOf("host.") === 0) {
      return;
    }
  };

  globalThis.__stremioRuntimeInitStatus = function () {
    flushTimers();
    return JSON.stringify({
      status: runtime.initStatus,
      coreReady: runtime.coreReady,
      errorMessage: runtime.initErrorMessage,
      startedAtMs: runtime.initStartedAtMs,
    });
  };

  globalThis.__stremioRuntimeInit = function (_payload) {
    flushTimers();
    ensureCoreInitialized();
    return runtimeResponse(drainEvents());
  };

  globalThis.__stremioAwaitInit = async function () {
    ensureCoreInitialized();
    await runtime.coreInitPromise;
    flushTimers();
    return JSON.stringify({
      status: runtime.initStatus,
      coreReady: runtime.coreReady,
      errorMessage: runtime.initErrorMessage,
    });
  };

  globalThis.__stremioRuntimeDispatch = function (payload) {
    flushTimers();
    ensureCoreInitialized();

    const action = payload && payload.action ? payload.action : null;
    const type = action && typeof action.type === "string" ? action.type : "";
    const actionPayload = action && action.payload && typeof action.payload === "object"
      ? action.payload
      : {};

    if (type === "runtime.initialize") {
      ensureCoreInitialized();
    } else if (type === "library.sync") {
      runtime.libraryReason = actionPayload.force ? "sync" : "mutation";
      refreshCatalogType("movie", true);
      refreshCatalogType("series", true);
    } else if (type === "playback.selectStream") {
      runtime.player.streamId = typeof actionPayload.streamId === "string" ? actionPayload.streamId : null;
      runtime.player.progressMs = 0;
      runtime.player.durationMs = null;

      const streamBase64 = typeof actionPayload.streamBase64 === "string"
        ? actionPayload.streamBase64
        : "";
      let decodedStream = null;
      let decodeFailed = false;

      if (streamBase64 !== "") {
        try {
          decodedStream = typeof globalThis.decodeStream === "function"
            ? globalThis.decodeStream(streamBase64)
            : streamBase64;
        } catch (error) {
          decodeFailed = true;
          pushEvent("runtime.error", {
            code: "playback_select_stream_decode_failed",
            message: String(error),
            recoverable: true,
            details: {
              streamId: runtime.player.streamId || "unknown",
            },
          });
        }
      }

      if (!decodeFailed) {
        const hasObjectLikeDecodedStream = !!decodedStream
          && typeof decodedStream === "object"
          && !Array.isArray(decodedStream);

        if (hasObjectLikeDecodedStream) {
          const dispatched = coreDispatch(
            {
              action: "Load",
              args: {
                model: "Player",
                args: {
                  stream: decodedStream,
                },
              },
            },
            "player",
            ""
          );
          if (!dispatched) {
            pushEvent("runtime.error", {
              code: "playback_select_stream_dispatch_failed",
              message: runtime.lastDispatchError || "Failed to dispatch player load action.",
              recoverable: true,
              details: {
                streamId: runtime.player.streamId || "unknown",
              },
            });
          }
        } else {
          pushEvent("runtime.error", {
            code: "playback_select_stream_invalid_stream",
            message: "Decoded stream payload was not object-like.",
            recoverable: true,
            details: {
              streamId: runtime.player.streamId || "unknown",
            },
          });
        }
      }

      pushEvent("playback.progress", {
        streamId: runtime.player.streamId || "unknown",
        progressMs: 0,
      });
    } else if (type === "playback.reportProgress") {
      if (typeof actionPayload.streamId === "string" && actionPayload.streamId.trim() !== "") {
        runtime.player.streamId = actionPayload.streamId;
      }
      const nextProgressMs = Number(actionPayload.progressMs || 0);
      runtime.player.progressMs = Number.isFinite(nextProgressMs) ? nextProgressMs : 0;
      if (typeof actionPayload.durationMs === "number" && Number.isFinite(actionPayload.durationMs)) {
        runtime.player.durationMs = actionPayload.durationMs;
      }

      const playerTimeChangedArgs = {
        time: runtime.player.progressMs,
        device: "android-host",
      };
      if (typeof runtime.player.durationMs === "number" && Number.isFinite(runtime.player.durationMs)) {
        playerTimeChangedArgs.duration = runtime.player.durationMs;
      }
      const dispatched = coreDispatch(
        {
          action: "Player",
          args: {
            action: "TimeChanged",
            args: playerTimeChangedArgs,
          },
        },
        "player",
        ""
      );
      if (!dispatched) {
        pushEvent("runtime.error", {
          code: "playback_report_progress_dispatch_failed",
          message: runtime.lastDispatchError || "Failed to dispatch player progress action.",
          recoverable: true,
          details: {
            streamId: runtime.player.streamId || "unknown",
            progressMs: runtime.player.progressMs,
          },
        });
      }
      pushEvent("playback.progress", {
        streamId: runtime.player.streamId || "unknown",
        progressMs: runtime.player.progressMs,
      });
    } else if (type.indexOf("custom.") === 0) {
      handleCustomAction(type.substring("custom.".length), actionPayload);
    }

    return runtimeResponse(drainEvents());
  };

  globalThis.__stremioRuntimeGetState = function (query) {
    flushTimers();
    ensureCoreInitialized();

    const scope = query && typeof query.scope === "string" ? query.scope : "custom";
    let data;

    if (scope === "session") {
      data = buildSessionState();
    } else if (scope === "library") {
      data = buildLibraryState();
    } else if (scope === "addons") {
      data = buildAddonsState();
    } else if (scope === "player") {
      data = buildPlayerState();
    } else {
      const key = query && typeof query.key === "string" ? query.key : "";
      if (key === "catalog") {
        data = buildCatalogState();
      } else if (key === "search") {
        data = buildSearchState();
      } else if (key === "meta") {
        data = buildMetaState();
      } else if (key === "settings") {
        data = buildSettingsState();
      } else {
        data = {};
      }
    }

    return runtimeResponse(drainEvents(), {
      snapshot: snapshot(scope, data),
    });
  };

  globalThis.__stremioRuntimeAnalytics = function (payload) {
    flushTimers();
    ensureCoreInitialized();

    if (payload && payload.event && typeof payload.event === "object") {
      const eventPayload = payload.event.payload && typeof payload.event.payload === "object"
        ? payload.event.payload
        : {};
      pushEvent("telemetry.event", {
        name: String(eventPayload.name || "analytics"),
        level: String(eventPayload.level || "info"),
        context: eventPayload.context && typeof eventPayload.context === "object" ? eventPayload.context : {},
      });
    }

    return runtimeResponse(drainEvents());
  };

  globalThis.__stremioRuntimeDecodeStream = function (payload) {
    flushTimers();
    ensureCoreInitialized();

    const streamBase64 = payload && typeof payload.streamBase64 === "string"
      ? payload.streamBase64
      : "";

    let decoded = streamBase64;
    try {
      if (typeof globalThis.decodeStream === "function") {
        decoded = globalThis.decodeStream(streamBase64);
      }
    } catch (_error) { }

    return {
      decoded: decoded,
    };
  };
})();
