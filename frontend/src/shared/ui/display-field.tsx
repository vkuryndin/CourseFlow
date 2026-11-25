import React from 'react';
import { Data, label } from '../api';
import { names } from '../labels';
import { formatDate } from '../datetime';
const timeFields = new Set(['createdAt', 'dueAt', 'endedAt', 'endsAt', 'enrolledAt', 'expiresAt', 'finishedAt', 'startsAt', 'submittedAt', 'takenAt', 'timestamp']);
export function displayField(key: string, value: unknown) { return key === 'startDate' ? formatDate(value, false) : timeFields.has(key) ? formatDate(value) : label(value); }
export const technicalKey = (key: string) => key !== 'requestId' && (/^(id|version|.*Id|.*Ids)$/.test(key) || ['accessToken', 'password', 'token'].includes(key));
export function Details({ data, keys }: {
    data: Data;
    keys?: string[];
}) { return <dl className="details">{(keys || Object.keys(data)).filter(k => data[k] !== undefined && !technicalKey(k) && (data[k] === null || typeof data[k] !== 'object' || Array.isArray(data[k]) && data[k].every((v: unknown) => typeof v !== 'object'))).map(k => <React.Fragment key={k}><dt>{names[k] || k}</dt><dd>{displayField(k, data[k])}</dd></React.Fragment>)}</dl>; }
