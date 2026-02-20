import React from "react";
import { createRoot } from "react-dom/client";

import { App } from "./App";
import { installNativePlaybackHandoffBridge } from "./patches/shared/native-playback-handoff";
import "./styles.css";

installNativePlaybackHandoffBridge();

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
