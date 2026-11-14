import { group, sleep } from 'k6';
import { createUser, getUser, listUsers, updateUser } from '../api/userApi.js';
import { expectStatus, idOf } from '../utils/checks.js';
import { everyNth, thinkTime } from '../utils/data.js';
import { userPayload } from '../data/payload.js';

const API = 'users';
const LIST_EVERY = Number(__ENV.USER_LIST_EVERY ?? 10);

/** Register -> view profile -> edit profile. Every Nth user also hits the full user list (admin view). */
export function userFlow() {
  group('User lifecycle', () => {
    const user = userPayload();
    const created = createUser(user);
    if (!expectStatus(created, 201, 'create user', API)) return;
    const id = idOf(created);
    sleep(thinkTime());

    expectStatus(getUser(id), 200, 'get user', API);
    sleep(thinkTime());

    expectStatus(updateUser(id, { ...user, name: `${user.name} (updated)` }), 200, 'update user', API);

    if (everyNth(LIST_EVERY)) {
      sleep(thinkTime());
      expectStatus(listUsers(), 200, 'list users', API);
    }
  });
}
