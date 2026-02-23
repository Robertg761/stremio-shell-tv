(function () {
  if (globalThis.__stremioRuntimeLoaded) {
    return;
  }
  globalThis.__stremioRuntimeLoaded = true;

  const CONTRACT_VERSION = 1;

  const state = {
    session: {
      isAuthenticated: false,
      userId: null,
    },
    library: {
      itemCount: 3,
      changedItemIds: ["movie-001", "series-042", "doc-204"],
      reason: "init",
    },
    player: {
      streamId: null,
      progressMs: 0,
      durationMs: null,
      selectedAtMs: null,
    },
    addons: {
      installed: ["catalog.base"],
    },
    custom: {
      catalog: {
        featuredIds: ["movie-001", "series-042", "anime-777"],
      },
      meta: {
        activeMetaId: null,
        title: null,
        subtitle: null,
      },
      search: {
        query: "",
        results: [],
      },
      settings: {
        theme: "emerald",
        subtitlesSize: "medium",
      },
    },
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

  const asArray = function (value) {
    return Array.isArray(value) ? value : [];
  };

  const runtimeResponse = function (events, extra) {
    const base = {
      events: events || [],
    };
    if (extra && typeof extra === "object") {
      Object.keys(extra).forEach(function (key) {
        base[key] = extra[key];
      });
    }
    return base;
  };

  const searchDataset = [
    "movie-001",
    "series-042",
    "anime-777",
    "doc-204",
    "kids-010",
    "retro-909",
  ];

  const setMetaFromId = function (id) {
    const normalizedId = String(id || "").trim();
    if (!normalizedId) {
      return;
    }

    state.custom.meta.activeMetaId = normalizedId;
    state.custom.meta.title = "Title for " + normalizedId;
    state.custom.meta.subtitle = "Metadata loaded in native runtime";
  };

  globalThis.__stremioRuntimeInit = function (payload) {
    const action = payload && payload.action ? payload.action : null;
    const source = action && action.payload && action.payload.source ? action.payload.source : "host";
    return runtimeResponse([
      envelope("runtime.initialized", { source: source }),
      envelope("runtime.raw", {
        rawEvent: {
          initialized: true,
          featuredIds: state.custom.catalog.featuredIds,
        },
      }),
      envelope("library.changed", {
        itemCount: state.library.itemCount,
        changedItemIds: state.library.changedItemIds,
        reason: state.library.reason,
      }),
    ]);
  };

  globalThis.__stremioRuntimeDispatch = function (payload) {
    const action = payload && payload.action ? payload.action : null;
    if (!action || typeof action.type !== "string") {
      return runtimeResponse([
        envelope("runtime.error", {
          code: "invalid_action",
          message: "Missing action envelope.",
          recoverable: true,
        }),
      ]);
    }

    const type = action.type;
    const actionPayload = action.payload || {};

    if (type === "runtime.initialize") {
      return runtimeResponse([
        envelope("runtime.initialized", {
          source: actionPayload.source || "host",
        }),
      ]);
    }

    if (type === "auth.login") {
      state.session.isAuthenticated = true;
      state.session.userId = actionPayload.email || actionPayload.token || "user";
      return runtimeResponse([
        envelope("auth.changed", {
          isAuthenticated: true,
          userId: state.session.userId,
        }),
      ]);
    }

    if (type === "auth.logout") {
      state.session.isAuthenticated = false;
      state.session.userId = null;
      return runtimeResponse([
        envelope("auth.changed", {
          isAuthenticated: false,
          userId: null,
        }),
      ]);
    }

    if (type === "library.sync") {
      const ids = asArray(actionPayload.changedItemIds);
      state.library.changedItemIds = ids.length > 0 ? ids : state.library.changedItemIds;
      state.library.itemCount = Math.max(state.library.itemCount, state.library.changedItemIds.length);
      state.library.reason = actionPayload.force ? "sync" : "mutation";
      return runtimeResponse([
        envelope("library.changed", {
          itemCount: state.library.itemCount,
          changedItemIds: state.library.changedItemIds,
          reason: state.library.reason,
        }),
      ]);
    }

    if (type === "playback.selectStream") {
      state.player.streamId = actionPayload.streamId || null;
      state.player.progressMs = 0;
      state.player.durationMs = null;
      state.player.selectedAtMs = now();
      return runtimeResponse([
        envelope("playback.progress", {
          streamId: state.player.streamId || "unknown",
          progressMs: 0,
        }),
      ]);
    }

    if (type === "playback.reportProgress") {
      state.player.streamId = actionPayload.streamId || state.player.streamId;
      state.player.progressMs = Number(actionPayload.progressMs || 0);
      state.player.durationMs = actionPayload.durationMs || state.player.durationMs;
      return runtimeResponse([
        envelope("playback.progress", {
          streamId: state.player.streamId || "unknown",
          progressMs: state.player.progressMs,
        }),
      ]);
    }

    if (type === "custom.updateSearch") {
      const query = String(actionPayload.query || "").trim().toLowerCase();
      state.custom.search.query = query;
      state.custom.search.results = searchDataset.filter(function (id) {
        return query.length === 0 || id.indexOf(query) >= 0;
      });
      return runtimeResponse([
        envelope("runtime.raw", {
          rawEvent: {
            searchUpdated: true,
            query: state.custom.search.query,
            count: state.custom.search.results.length,
          },
        }),
      ]);
    }

    if (type === "custom.selectMeta") {
      setMetaFromId(actionPayload.id);
      return runtimeResponse([
        envelope("runtime.raw", {
          rawEvent: {
            metaSelected: state.custom.meta.activeMetaId,
          },
        }),
      ]);
    }

    if (type === "custom.installAddon") {
      const name = String(actionPayload.name || "").trim();
      if (name && state.addons.installed.indexOf(name) < 0) {
        state.addons.installed.push(name);
      }
      return runtimeResponse([
        envelope("runtime.raw", {
          rawEvent: {
            addonInstalled: name,
            addons: state.addons.installed,
          },
        }),
      ]);
    }

    if (type === "custom.removeAddon") {
      const name = String(actionPayload.name || "").trim();
      state.addons.installed = state.addons.installed.filter(function (item) {
        return item !== name;
      });
      return runtimeResponse([
        envelope("runtime.raw", {
          rawEvent: {
            addonRemoved: name,
            addons: state.addons.installed,
          },
        }),
      ]);
    }

    if (type === "custom.updateSetting") {
      const key = String(actionPayload.key || "").trim();
      const value = String(actionPayload.value || "").trim();
      if (key) {
        state.custom.settings[key] = value;
      }
      return runtimeResponse([
        envelope("runtime.raw", {
          rawEvent: {
            settingUpdated: key,
            value: value,
          },
        }),
      ]);
    }

    if (type.indexOf("custom.") === 0) {
      state.custom.lastAction = {
        type: type,
        payload: actionPayload,
        atMs: now(),
      };
      return runtimeResponse([
        envelope("runtime.raw", {
          rawEvent: state.custom.lastAction,
        }),
      ]);
    }

    return runtimeResponse([
      envelope("runtime.error", {
        code: "unsupported_action",
        message: "Unsupported action type: " + type,
        recoverable: true,
      }),
    ]);
  };

  globalThis.__stremioRuntimeGetState = function (query) {
    const scope = query && typeof query.scope === "string" ? query.scope : "custom";
    const key = query && typeof query.key === "string" ? query.key : "";

    if (scope === "session") {
      return { snapshot: snapshot("session", state.session) };
    }
    if (scope === "library") {
      return { snapshot: snapshot("library", state.library) };
    }
    if (scope === "player") {
      return { snapshot: snapshot("player", state.player) };
    }
    if (scope === "addons") {
      return { snapshot: snapshot("addons", state.addons) };
    }

    if (scope === "custom") {
      if (key === "catalog") {
        return { snapshot: snapshot("custom", state.custom.catalog) };
      }
      if (key === "meta") {
        return { snapshot: snapshot("custom", state.custom.meta) };
      }
      if (key === "search") {
        return { snapshot: snapshot("custom", state.custom.search) };
      }
      if (key === "settings") {
        return { snapshot: snapshot("custom", state.custom.settings) };
      }
      return { snapshot: snapshot("custom", state.custom) };
    }

    return { snapshot: snapshot(scope, {}) };
  };

  globalThis.__stremioRuntimeAnalytics = function (payload) {
    const event = payload && payload.event ? payload.event : null;
    const eventPayload = event && event.payload ? event.payload : {};
    return runtimeResponse([
      envelope("telemetry.event", {
        name: eventPayload.name || "unknown",
        level: eventPayload.level || "info",
        context: eventPayload.context || {},
      }),
    ]);
  };

  globalThis.__stremioRuntimeDecodeStream = function (payload) {
    const streamBase64 = payload && payload.streamBase64 ? String(payload.streamBase64) : "";
    return {
      decoded: {
        streamBase64: streamBase64,
      },
    };
  };
})();
