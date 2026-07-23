import http from 'k6/http';
import { check, sleep } from 'k6';

const TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJleHAiOjE3ODQ3NjcyMjV9.CIlJlDF-eaMyaPy--f0xg9qXxLTuNljAFwuU7OCsiOM';

export const options = {
    vus: 10,
    duration: '15s',
};

export default function () {
    const res = http.get('http://localhost:8080/api/v1/hello', {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'status is 429': (r) => r.status === 429,
    });

    sleep(0.1);
}