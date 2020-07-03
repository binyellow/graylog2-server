// @flow strict
import * as React from 'react';
import { Formik, Form, Field } from 'formik';
import styled, { type StyledComponent } from 'styled-components';

import { type ThemeInterface } from 'theme';
import EntityShareState, { type AvailableGrantees, type AvailableCapabilities } from 'logic/permissions/EntityShareState';
import Capability from 'logic/permissions/Capability';
import Grantee from 'logic/permissions/Grantee';
import { Button } from 'components/graylog';
import { Select } from 'components/common';
import SelectGroup from 'components/common/SelectGroup';

import GranteeIcon from './GranteeIcon';
import CapabilitySelect from './CapabilitySelect';

export type SelectionRequest = {
  granteeId: $PropertyType<Grantee, 'id'>,
  capabilityId: $PropertyType<Capability, 'id'>,
};

type Props = {
  availableGrantees: AvailableGrantees,
  availableCapabilities: AvailableCapabilities,
  className?: string,
  onSubmit: SelectionRequest => Promise<EntityShareState>,
};

const FormElements = styled.div`
  display: flex;
`;

const Errors: StyledComponent<{}, ThemeInterface, HTMLDivElement> = styled.div(({ theme }) => `
  width: 100%;
  margin-top: 3px;
  color: ${theme.colors.variant.danger};

  > * {
    margin-right: 5px;

    :last-child {
      margin-right: 0;
    }
  }
`);

const GranteesSelect = styled(Select)`
  flex: 1;
`;

const GranteesSelectOption = styled.div`
  display: flex;
  align-items: center;
`;

const StyledGranteeIcon = styled(GranteeIcon)`
  margin-right: 5px;
`;

const StyledSelectGroup = styled(SelectGroup)`
  flex: 1;

  > div:last-child {
    flex: 0.5;
  }
`;

const SubmitButton = styled(Button)`
  margin-left: 15px;
`;

const _granteesOptions = (grantees: AvailableGrantees) => {
  return grantees.map((grantee) => (
    { label: grantee.title, value: grantee.id, granteeType: grantee.type }
  )).toJS();
};

const _initialCapabilityId = (capabilities: AvailableCapabilities) => {
  const initialCapabilityTitle = 'Viewer';

  return capabilities.find((capability) => capability.title === initialCapabilityTitle)?.id;
};

const _isRequired = (field) => (value) => (!value ? `The ${field} is required` : undefined);

const _renderGranteesSelectOption = ({ label, granteeType }: {label: string, granteeType: $PropertyType<Grantee, 'type'> }) => (
  <GranteesSelectOption>
    <StyledGranteeIcon type={granteeType} />
    {label}
  </GranteesSelectOption>
);

const GranteesSelector = ({ availableGrantees, availableCapabilities, className, onSubmit }: Props) => {
  const granteesOptions = _granteesOptions(availableGrantees);
  const initialCapabilityId = _initialCapabilityId(availableCapabilities);

  return (
    <div className={className}>
      <Formik onSubmit={(data) => onSubmit(data)}
              initialValues={{ granteeId: undefined, capabilityId: initialCapabilityId }}>
        {({ isSubmitting, isValid, errors }) => (
          <Form>
            <FormElements>
              <StyledSelectGroup>
                <Field name="granteeId" validate={_isRequired('grantee')}>
                  {({ field: { name, value, onChange } }) => (
                    <GranteesSelect inputProps={{ 'aria-label': 'Search for users and teams' }}
                                    onChange={(granteeId) => onChange({ target: { value: granteeId, name } })}
                                    optionRenderer={_renderGranteesSelectOption}
                                    options={granteesOptions}
                                    placeholder="Search for users and teams"
                                    value={value} />
                  )}
                </Field>
                <CapabilitySelect capabilities={availableCapabilities} />
              </StyledSelectGroup>
              <SubmitButton bsStyle="success"
                            disabled={isSubmitting || !isValid}
                            title="Add Collaborator"
                            type="submit">
                Add Collaborator
              </SubmitButton>
            </FormElements>
            {errors && (
              <Errors>
                {Object.entries(errors).map(([fieldKey, value]: [string, mixed]) => (
                  <span key={fieldKey}>{String(value)}.</span>
                ))}
              </Errors>
            )}
          </Form>
        )}
      </Formik>
    </div>
  );
};

GranteesSelector.defaultProps = {
  className: undefined,
};

export default GranteesSelector;
