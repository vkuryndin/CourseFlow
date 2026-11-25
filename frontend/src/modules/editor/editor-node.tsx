import { Data } from '../../shared/api';
export type Node = {
    kind: string;
    data: Data;
    m?: Data;
    l?: Data;
    q?: Data;
};
