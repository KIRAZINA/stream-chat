import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { rest } from "msw";
import { beforeAll, afterAll, afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import DashboardPage from "../pages/DashboardPage";
import { useAuthStore } from "../stores/auth-store";
import { server } from "./server";

const navigate = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  navigate.mockReset();
  useAuthStore.setState({
    user: null,
    token: null,
    refreshToken: null,
    expiryTime: null,
  });
});
afterAll(() => server.close());

const renderDashboard = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

describe("DashboardPage stream creation", () => {
  it("sends bearer token and navigates to the created stream", async () => {
    const user = userEvent.setup();
    const createRequest = vi.fn();

    useAuthStore.setState({
      token: "access-token",
      refreshToken: "refresh-token",
      expiryTime: Date.now() + 60_000,
    });

    server.use(
      rest.get("http://localhost:8080/api/streams", (_req, res, ctx) => res(ctx.json([]))),
      rest.post("http://localhost:8080/api/streams", async (req, res, ctx) => {
        createRequest({
          authorization: req.headers.get("authorization"),
          body: await req.json(),
        });

        return res(
          ctx.status(201),
          ctx.json({
            id: 1,
            streamKey: "stream-123",
            title: "Launch stream",
            description: null,
            ownerId: 1,
            ownerUsername: "alice",
            status: "OFFLINE",
            viewerCount: 0,
            createdAt: new Date().toISOString(),
          }),
        );
      }),
    );

    renderDashboard();

     await user.type(screen.getByLabelText(/title/i), "Launch stream");
     await user.click(screen.getByRole("button", { name: /create/i }));

    await waitFor(() => {
      expect(createRequest).toHaveBeenCalledWith({
        authorization: "Bearer access-token",
        body: {
          title: "Launch stream",
          category: "Music",
        },
      });
      expect(navigate).toHaveBeenCalledWith("/stream/stream-123");
    });
  });
});
