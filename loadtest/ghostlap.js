// GhostLap load test (k6). Each virtual user (VU) is one player:
//   1. registers once (first iteration),
//   2. then loops: submit a lap, read the leaderboard 3 times.
//
// Run it inside Docker, on the same network as the services (no k6 install needed):
//   cd infra
//   docker compose --profile loadtest run --rm k6
//
// The lap is docs/examples/lap-16s.json: a valid 16 s lap, so every submission is ACCEPTED and
// stored (lap row + ghost bytes in Postgres, leaderboard check in Redis).
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const PLAYER_URL = __ENV.PLAYER_URL || 'http://player-service:8081';
const RACE_URL = __ENV.RACE_URL || 'http://race-service:8082';
const LAP = open('/data/lap-16s.json'); // read once, shared by all VUs

export const options = {
  scenarios: {
    players: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 50 }, // ramp up to 50 players
        { duration: '60s', target: 50 }, // hold
        { duration: '10s', target: 0 },  // ramp down
      ],
    },
  },
  // Pass/fail rules. Listing each endpoint here also makes k6 print its own latency line.
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:register}': ['p(95)<2000'],
    'http_req_duration{name:submit lap}': ['p(95)<500'],
    'http_req_duration{name:leaderboard}': ['p(95)<200'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

// Each VU runs this function in a loop. Variables outside it are per VU, so `token` is remembered.
let token = null;

export default function () {
  if (token === null) {
    register();
  }
  const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };

  const lap = http.post(`${RACE_URL}/api/v1/laps`, LAP, { headers: auth, tags: { name: 'submit lap' } });
  check(lap, { 'lap accepted': (r) => r.status === 201 && r.json('status') === 'ACCEPTED' });

  for (let i = 0; i < 3; i++) {
    const board = http.get(`${RACE_URL}/api/v1/tracks/1/leaderboard?limit=10`, { tags: { name: 'leaderboard' } });
    check(board, { 'leaderboard 200': (r) => r.status === 200 });
  }
  sleep(0.1); // a short pause between rounds, like a player between laps (very impatient players)
}

function register() {
  // Usernames: 3-20 letters/digits/_ , unique per VU and per run.
  const username = `load_${exec.vu.idInTest}_${Math.floor(Math.random() * 1e6)}`;
  const res = http.post(`${PLAYER_URL}/api/v1/auth/register`,
      JSON.stringify({ username, password: 'password123' }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'register' } });
  check(res, { 'registered': (r) => r.status === 201 });
  token = res.json('token');
}
