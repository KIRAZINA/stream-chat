import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { rest } from "msw";
import { beforeAll, afterAll, afterEach, describe, expect, it, vi } from "vitest";
import StreamSettingsForm from "../components/streams/StreamSettingsForm";
import { server } from "./server";

const getSettings = vi.fn();
const putSettings = vi.fn();

const baseSettings = {
  id: 1,
  slowModeEnabled: false,
  slowModeSeconds: 10,
  followersOnlyMode: false,
  followersOnlyDurationMinutes: 30,
  subscribersOnlyMode: false,
  emoteOnlyMode: false,
  maxMessageLength: 500,
  profanityFilterEnabled: true,
  linkProtectionEnabled: true,
};

const enabledSettings = { ...baseSettings, slowModeEnabled: true, followersOnlyMode: true };

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  getSettings.mockReset();
  putSettings.mockReset();
});
afterAll(() => server.close());

const renderForm = (settings = baseSettings) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  server.use(
    rest.get(/\/api\/streams\/test-stream\/settings/, (_req, res, ctx) => {
      getSettings();
      return res(ctx.json(settings));
    }),
    rest.put(/\/api\/streams\/test-stream\/settings/, async (req, res, ctx) => {
      putSettings(await req.json());
      return res(ctx.json({ ...settings, slowModeEnabled: true }));
    }),
  );

  return render(
    <QueryClientProvider client={queryClient}>
      <StreamSettingsForm streamKey="test-stream" />
    </QueryClientProvider>,
  );
};

const waitForLoaded = async () => {
  await screen.findByText("Stream Settings");
  await waitFor(() => {
    expect(getSettings).toHaveBeenCalled();
  });
  await waitFor(() => {
    expect(screen.getByRole("button", { name: /save settings/i })).toBeEnabled();
  });
};

describe("StreamSettingsForm", () => {
  it("reflects fetched settings in the checkboxes", async () => {
    renderForm(enabledSettings);
    await waitForLoaded();

    expect(screen.getByRole("checkbox", { name: /slow mode/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /followers only/i })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /subscribers only/i })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: /emotes only/i })).not.toBeChecked();
  });

  it("saves toggled settings to the backend", async () => {
    const user = userEvent.setup();
    renderForm();
    await waitForLoaded();

    const slowMode = screen.getByRole("checkbox", { name: /slow mode/i }) as HTMLInputElement;
    await user.click(slowMode);

    await user.click(screen.getByRole("button", { name: /save settings/i }));

    await waitFor(() => {
      expect(putSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          slowModeEnabled: true,
          slowModeSeconds: 10,
          maxMessageLength: 500,
        }),
      );
    });
  });

  it("reveals the slow-mode duration input when slow mode is toggled on", async () => {
    const user = userEvent.setup();
    renderForm();
    await waitForLoaded();

    expect(screen.queryByLabelText(/slow mode \(seconds\)/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /slow mode/i }));

    const secondsInput = screen.getByLabelText(/slow mode \(seconds\)/i) as HTMLInputElement;
    await user.clear(secondsInput);
    await user.type(secondsInput, "15");

    await user.click(screen.getByRole("button", { name: /save settings/i }));

    await waitFor(() => {
      expect(putSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          slowModeEnabled: true,
          slowModeSeconds: 15,
        }),
      );
    });
  });

  it("reveals the followers-only duration input when followers-only is toggled on", async () => {
    const user = userEvent.setup();
    renderForm();
    await waitForLoaded();

    expect(screen.queryByLabelText(/followers-only duration/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /followers only/i }));

    expect(screen.getByLabelText(/followers-only duration/i)).toBeInTheDocument();
  });
});