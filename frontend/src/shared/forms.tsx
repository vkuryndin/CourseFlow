// Public module entry point. Keep callers independent of internal file layout.
export {schema} from './forms/schema';
export {initialValues} from './forms/schema';
export {buildPayload} from './forms/schema';
export type {FormSpec} from './forms/entity-form';
export {EntityForm} from './forms/entity-form';
export {EditPanel} from './forms/entity-form';
export {useAction} from './forms/actions';
